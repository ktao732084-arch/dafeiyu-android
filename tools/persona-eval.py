#!/usr/bin/env python3
"""Can you tell her styles apart? A blind check of persona distinctiveness against a real model.

1. Dump the exact requests the app would send (see PersonaEvalDump.java), e.g. on Windows after run-core.ps1:
     java -cp "build/compile-check/classes;build/core-tests;<org.json jar>" com.xiaoxiao.pet.PersonaEvalDump assets/personas.json build/persona-requests.json
   For a baseline, dump an older personas.json the same way (git show <ref>:assets/personas.json > old.json).
2. Run this script with your own key (it never stores or prints it):
     DAFEIYU_API_KEY=sk-... python tools/persona-eval.py build/persona-requests.json --out build/persona-eval.md

Each reply is then shown, without its style, to a judge call at temperature 0 that picks one of the five
style names and descriptions (order shuffled per question). Accuracy well above 20% (chance) means the styles
read differently; the confusion matrix shows which ones blur together. The report and raw replies stay local:
do not commit them, since they come from your key and account.
"""
import argparse, concurrent.futures, json, os, random, re, sys, urllib.parse, urllib.request
from collections import Counter, defaultdict

DEFAULT_URL = "https://api.deepseek.com/chat/completions"


def call(url, key, model, messages, temperature, max_tokens):
    payload = {"model": model, "messages": messages, "stream": False, "max_tokens": max_tokens}
    host = urllib.parse.urlparse(url).hostname or ""
    if host == "api.deepseek.com" and (model == "deepseek-flash" or model.startswith("deepseek-v4-")):
        payload["thinking"] = {"type": "disabled"}  # as ModelPresets.apply
    if temperature is not None:
        payload["temperature"] = temperature
    request = urllib.request.Request(url, json.dumps(payload).encode("utf-8"),
                                     {"Content-Type": "application/json", "Authorization": "Bearer " + key})
    with urllib.request.urlopen(request, timeout=120) as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["choices"][0]["message"]["content"] or ""


def reply_text(raw):
    text = re.sub(r"(?s)<think>.*?</think>", "", raw).strip()
    text = re.sub(r"(?s)^```(?:json)?\s*|\s*```$", "", text)
    start, end = text.find("{"), text.rfind("}")
    if start >= 0 and end > start:
        try:
            value = json.loads(text[start:end + 1]).get("reply")
            if isinstance(value, str):
                return value.strip()
        except ValueError:
            pass
    return text


def judge(url, key, model, styles, reply, probe, rng):
    order = styles[:]
    rng.shuffle(order)
    options = "\n".join(f"{i + 1}. {s['name']}：{s['description']}" for i, s in enumerate(order))
    question = ("下面是一个陪伴角色对用户一句话的回复。这个角色只可能是以下几种性格之一：\n" + options +
                f"\n\n用户：{probe}\n角色：{reply}\n\n只根据说话方式、语气和立场判断最可能是哪一种，只输出编号数字。")
    answer = call(url, key, model, [{"role": "user", "content": question}], 0, 16)
    match = re.search(r"\d+", answer)
    index = int(match.group()) - 1 if match else -1
    return order[index]["id"] if 0 <= index < len(order) else "?"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("requests", help="JSON written by PersonaEvalDump")
    parser.add_argument("--out", default="persona-eval.md")
    parser.add_argument("--url", default=os.environ.get("DAFEIYU_API_URL", DEFAULT_URL), help="chat completions endpoint")
    parser.add_argument("--model", default=os.environ.get("DAFEIYU_MODEL", "deepseek-flash"))
    parser.add_argument("--judge-model", default=None, help="defaults to --model")
    parser.add_argument("--temperature", type=float, default=None, help="defaults to the app's choice for this provider")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    key = os.environ.get("DAFEIYU_API_KEY")
    if not key:
        sys.exit("Set DAFEIYU_API_KEY to your own API key.")
    host = urllib.parse.urlparse(args.url).hostname or ""
    temperature = args.temperature if args.temperature is not None else (1.0 if host == "api.deepseek.com" else None)
    data = json.load(open(args.requests, encoding="utf-8"))
    styles, cases = data["styles"], data["cases"]
    names = {s["id"]: s["name"] for s in styles}

    def run(case):
        try:
            return reply_text(call(args.url, key, args.model, case["messages"], temperature, 1200))
        except Exception as error:  # one failed request should not sink the whole run
            return "[请求失败：" + type(error).__name__ + "]"

    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool:
        replies = list(pool.map(run, cases))
        rng = random.Random(args.seed)
        seeds = [rng.random() for _ in cases]
        guesses = list(pool.map(lambda i: judge(args.url, key, args.judge_model or args.model, styles, replies[i],
                                                cases[i]["probe"], random.Random(seeds[i]))
                                if not replies[i].startswith("[请求失败") else "?", range(len(cases))))

    confusion = defaultdict(Counter)
    for case, guess in zip(cases, guesses):
        confusion[case["style"]][guess] += 1
    correct = sum(1 for case, guess in zip(cases, guesses) if case["style"] == guess)
    lines = [f"# 性格辨识度测试", "",
             f"模型 `{args.model}`，温度 {temperature if temperature is not None else '服务商默认'}，"
             f"{len(styles)} 种性格 × {len(cases) // max(1, len(styles))} 句话。随机猜的正确率约 {100 // len(styles)}%。", "",
             f"**总体辨识正确率：{correct}/{len(cases)}（{100 * correct / len(cases):.0f}%）**", "",
             "| 实际性格 | 认对 | " + " | ".join(names[s["id"]] for s in styles) + " | 无法判断 |",
             "|---|---|" + "---|" * (len(styles) + 1)]
    for s in styles:
        row = confusion[s["id"]]
        total = sum(row.values())
        lines.append(f"| {s['name']} | {row[s['id']]}/{total} | " + " | ".join(str(row[t["id"]]) for t in styles) + f" | {row['?']} |")
    lines += ["", "## 回复对照", ""]
    by_probe = defaultdict(dict)
    for case, reply, guess in zip(cases, replies, guesses):
        by_probe[case["probe"]][case["style"]] = (reply, guess)
    for probe, row in by_probe.items():
        lines += [f"### 用户：{probe}", ""]
        for s in styles:
            reply, guess = row.get(s["id"], ("", "?"))
            mark = "✓" if guess == s["id"] else f"✗ 被认成 {names.get(guess, '无法判断')}"
            lines.append(f"- **{s['name']}**（{mark}）：{reply}")
        lines.append("")
    open(args.out, "w", encoding="utf-8").write("\n".join(lines))
    json.dump([{"style": c["style"], "probe": c["probe"], "reply": r, "guess": g} for c, r, g in zip(cases, replies, guesses)],
              open(os.path.splitext(args.out)[0] + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{correct}/{len(cases)} recognised; report written to {args.out}")


if __name__ == "__main__":
    main()
