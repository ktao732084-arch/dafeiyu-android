"""Summarize opt-in expanded eval; raw replies remain local. Not a model-quality oracle."""
import argparse,collections,hashlib,json,pathlib,random,re

def main():
 p=argparse.ArgumentParser();p.add_argument('run');p.add_argument('--out');p.add_argument('--review');args=p.parse_args()
 root=pathlib.Path(args.run);manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
 rows=[]
 for file in sorted((root/'rows').glob('*.json')):
  r=json.loads(file.read_text(encoding='utf-8'))
  if r.get('complete'):rows.append(r)
 extraction=[r for r in rows if r['kind']=='extract'];chat=[r for r in rows if r['kind']!='extract']
 def metrics(sample,field):
  scored=[r for r in sample if field in r];tp=sum(r[field]['tp'] for r in scored);fp=sum(r[field]['fp'] for r in scored);fn=sum(r[field]['fn'] for r in scored)
  return dict(rows=len(scored),tp=tp,fp=fp,fn=fn,precision=round(tp/(tp+fp),4) if tp+fp else None,recall=round(tp/(tp+fn),4) if tp+fn else None,
   exact=sum(r[field]['exact'] for r in scored),strict_exact=sum(r[field]['exact'] and 'error' not in r for r in scored))
 def verdicts(sample):return dict(collections.Counter('product_error' if 'error' in r else r.get('judge',{}).get('verdict','ungraded') for r in sample))
 groups=collections.defaultdict(list)
 for r in rows:groups[r['case']].append(r)
 cases={}
 for name,sample in sorted(groups.items()):
  first=sample[0];x=dict(kind=first['kind'],category=first['category'],scope=first['scope'],rows=len(sample),errors=sum('error' in r for r in sample))
  if first['kind']=='extract':x.update(local=metrics(sample,'local_score'),model=metrics(sample,'model_score'),pipeline=metrics(sample,'pipeline_score'))
  else:x['judges']=verdicts(sample)
  cases[name]=x
 paired=[r for r in chat if 'judge_repeat' in r];disagree=[r['id'] for r in paired if r['judge']['verdict']!=r['judge_repeat']['verdict']]
 core=[r for r in extraction if r['scope']=='core'];challenge=[r for r in extraction if r['scope']=='challenge']
 comparable=[r for r in core if r['category'] not in {'privacy','visibility','budget','length'}]
 def target_key(g):return json.dumps(g,sort_keys=True,ensure_ascii=False)
 filtered=0;filtered_rows=[]
 for r in core:
  if 'model_score' not in r:continue
  model_missing={target_key(g) for g in r['model_score']['missing']};pipe_missing={target_key(g) for g in r['pipeline_score']['missing']}
  n=len(pipe_missing-model_missing);filtered+=n
  if n:filtered_rows.append(r['id'])
 review={}
 for case,sample in groups.items():
  if sample[0]['kind']=='extract':
   bad=[r for r in sample if not r['pipeline_score']['exact'] or 'error' in r]
   # One failure per scenario/style, plus every distinct error; do not drown qualitative review in deterministic duplicates.
   for style in sorted({r['style'] for r in bad}):
    chosen=next(r for r in bad if r['style']==style);review[chosen['id']]='extraction failure sample'
  else:
   for r in sample:
    if 'error' in r or r.get('judge',{}).get('verdict')!='pass' or r['id'] in disagree:review[r['id']]='judge failure/uncertainty/error/disagreement'
 for style in sorted({r['style'] for r in chat}):
  passed=[r for r in chat if r['style']==style and 'error' not in r and r.get('judge',{}).get('verdict')=='pass']
  random.Random('audit-'+style).shuffle(passed)
  for r in passed[:5]:review.setdefault(r['id'],'stratified passed sample')
 result=dict(manifest=manifest,completed_rows=len(rows),product_errors=dict(collections.Counter(r['error'] for r in rows if 'error' in r)),fatal_runs=len(list((root/'fatal').glob('*.json'))),
  extraction_core=dict(local=metrics(core,'local_score'),pipeline=metrics(core,'pipeline_score')),
  extraction_challenge=metrics(challenge,'pipeline_score'),model_comparable=metrics(comparable,'model_score'),
  same_subset_comparison={field:metrics(comparable,field) for field in ['local_score','model_score','pipeline_score']},
  model_supported_targets_missing_from_pipeline=filtered,model_supported_filtered_rows=filtered_rows,
  conversation_judges=verdicts(chat),by_style={style:dict(core=metrics([r for r in core if r['style']==style],'pipeline_score'),judges=verdicts([r for r in chat if r['style']==style])) for style in sorted({r['style'] for r in rows})},
  repeated_judgments=dict(pairs=len(paired),disagreements=len(disagree),ids=disagree),cases=cases,review_rows=review,
  caveats=['Style/repeat rows are correlated; scenario count, not request count, defines linguistic coverage.',
   'Model raw proposal scoring is not directly comparable to pipeline scoring on visibility/privacy/length/budget controls.',
   'Conversation verdicts are same-model automated assessments until reviewed; do not label them independent human accuracy.'])
 guarded=[r for r in chat if r.get('reply_guard_applied')]
 result['response_boundary']=dict(applied=len(guarded),body_changed=sum(r.get('model_reply')!=r.get('reply') for r in guarded),aside_changed=sum(r.get('model_aside')!=r.get('aside') for r in guarded),ids=[r['id'] for r in guarded])
 artifacts=re.compile(r'<(?:reply|aside|context|背景|emotion|最后)>|\[reply\]|next_check_minutes|mood_level')
 result['protocol_artifact_ids']=[r['id'] for r in chat if artifacts.search(r.get('reply',''))]
 if args.review:
  review_path=pathlib.Path(args.review);audit=json.loads(review_path.read_text(encoding='utf-8'));labels=audit['rows']
  assert len(labels)==len(chat) and {r['id'] for r in labels}=={r['id'] for r in chat},'Review must cover every chat row once'
  originals={r['id']:'product_error' if 'error' in r else r.get('judge',{}).get('verdict','ungraded') for r in chat}
  assert all(r['automatic_verdict']==originals[r['id']] for r in labels),'Review does not match this run'
  assert dict(collections.Counter(r['verdict'] for r in labels))==audit['counts'],'Review counts do not match labels'
  result['conversation_review']={k:audit[k] for k in ['reviewer','rubric','counts','by_case','automatic_to_reviewed']}
  result['conversation_review']['sha256']=hashlib.sha256(review_path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
 result['evidence_hashes']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'rows').glob('*.json'))}
 out=pathlib.Path(args.out) if args.out else root/'summary.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps({k:result[k] for k in ['completed_rows','product_errors','fatal_runs','extraction_core','extraction_challenge','conversation_judges','repeated_judgments']},ensure_ascii=False,indent=2))
 # Compact local review text includes replies, but never credentials or full system prompts.
 by_id={r['id']:r for r in rows};lines=[]
 for rid,why in sorted(review.items()):
  r=by_id[rid];lines.append(json.dumps(dict(id=rid,why=why,user=r.get('user'),reply=r.get('reply'),error=r.get('error'),rules=r.get('rules'),judge=r.get('judge'),judge_repeat=r.get('judge_repeat'),gold=r.get('gold'),accepted=r.get('accepted'),pipeline_score=r.get('pipeline_score'),provided_commitments=r.get('provided_commitments')),ensure_ascii=False))
 (root/'review.jsonl').write_text('\n'.join(lines)+'\n',encoding='utf-8')

if __name__=='__main__':main()
