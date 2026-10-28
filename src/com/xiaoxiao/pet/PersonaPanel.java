package com.xiaoxiao.pet;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** First meeting chooses one stable personality. Afterwards this page is read-only. */
final class PersonaPanel {
    static void show(MainActivity activity, String role) {
        try {
            boolean initialized = PersonaManager.ensureInitialized(role);
            Dialog dialog = ClientPage.create(activity);
            LinearLayout page = activity.column();int pad = activity.dp(16);
            page.setPadding(pad, activity.dp(8), pad, pad);
            LinearLayout header = new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(Apple.label(activity, initialized ? "她的性格" : "初次相遇", 22, true), new LinearLayout.LayoutParams(0, -2, 1));
            Button close = new Button(activity);close.setText(initialized ? "关闭" : "稍后");Apple.button(close, false);
            close.setOnClickListener(view -> dialog.dismiss());
            header.addView(close, new LinearLayout.LayoutParams(activity.dp(68), activity.dp(48)));page.addView(header);
            ScrollView scroll = new SpringScrollView(activity);scroll.setFillViewport(true);
            LinearLayout body = activity.column();scroll.addView(body);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            if (initialized) readOnly(activity, role, body);else firstMeeting(activity, role, dialog, body);
            dialog.setContentView(page);ClientPage.show(dialog, "大肥鱼-" + (initialized ? "她的性格" : "初次相遇"));
        } catch (Exception error) { AgentPanel.fail(activity, error); }
    }

    private static void note(MainActivity a, LinearLayout body, String value) {
        TextView text = Apple.label(a, value, 14, false);text.setTextColor(Apple.SECONDARY);
        text.setLineSpacing(a.dp(4), 1);text.setPadding(0, a.dp(14), 0, a.dp(14));body.addView(text);
    }

    private static void hello(MainActivity a, LinearLayout body) {
        View face = new View(a);face.setBackground(Avatar.of(a, "main", 80));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(a.dp(80), a.dp(80));size.gravity = Gravity.CENTER_HORIZONTAL;size.topMargin = a.dp(12);
        body.addView(face, size);
    }

    private static void readOnly(MainActivity a, String role, LinearLayout body) {
        hello(a, body);
        TextView name = Apple.label(a, PersonaManager.label(role), 22, true);
        name.setGravity(Gravity.CENTER);name.setPadding(0, a.dp(14), 0, 0);name.setContentDescription("已固定人格：" + PersonaManager.label(role));body.addView(name);
        String mode = PersonaManager.mode(role);
        String source = PersonaManager.RANDOM.equals(mode) ? "初次相遇时随机遇见的她。" :
                PersonaManager.LEGACY.equals(mode) || PersonaManager.CUSTOM.equals(mode) ? "沿用升级前的性格和你们的共同经历。" : "初次相遇时，你选了这样的她。";
        note(a, body, source + "\n性格已经定下来了；她还是会有小情绪，也会在相处中越来越懂你。清空聊天或记忆都不会改变她的性格。");
        if (!PersonaManager.CUSTOM.equals(mode)) {
            PersonaManager.Persona item = PersonaManager.find(PersonaManager.activeId(role));
            if (item != null) note(a, body, item.description);
        }
    }

    private static void firstMeeting(MainActivity a, String role, Dialog dialog, LinearLayout body) {
        hello(a, body);
        note(a, body, "想遇见一只怎样的大肥鱼？\n可以亲自挑，也可以交给缘分。确认之后她就一直是这个性格了，以后不能再换哦。");
        RadioGroup choices = new RadioGroup(a);choices.setOrientation(LinearLayout.VERTICAL);
        final String[] selection = {PersonaManager.DEFAULT_ID};
        int index = 100;
        for (PersonaManager.Persona item : PersonaManager.presets()) {
            RadioButton option = option(a, choices, ++index, item.name + "\n" + item.description);
            option.setContentDescription("初始人格：" + item.name);
            if (PersonaManager.DEFAULT_ID.equals(item.id)) option.setChecked(true);
            option.setOnCheckedChangeListener((button, checked) -> { if (checked) selection[0] = item.id; });
        }
        RadioButton random = option(a, choices, ++index, "交给缘分\n从这 5 种性格里随机遇见一位，遇见了就不变。");
        random.setContentDescription("初始化随机人格");
        random.setOnCheckedChangeListener((button, checked) -> { if (checked) selection[0] = PersonaManager.RANDOM; });
        body.addView(choices);
        note(a, body, "5 种说话风格是这一版新写的。随机只在这次相遇时抽一次，重开 App 也不会变。");
        Button confirm = a.button(body, "确认性格，开始陪伴", view -> {
            try {
                String chosen = selection[0];
                if (PersonaManager.RANDOM.equals(chosen)) PersonaManager.initializeRandom(role);else PersonaManager.initialize(role, chosen);
                a.render();dialog.dismiss();
                Toast.makeText(a, "遇见啦！她是「" + PersonaManager.label(role) + "」", Toast.LENGTH_LONG).show();
            } catch (Exception error) { AgentPanel.fail(a, error); }
        });
        Apple.button(confirm, true);
    }

    private static RadioButton option(MainActivity a, RadioGroup group, int id, String text) {
        RadioButton option = new RadioButton(a);option.setId(id);option.setText(text);
        Apple.choice(option);option.setMinHeight(a.dp(76));
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(-1, -2);params.topMargin = a.dp(10);
        group.addView(option, params);return option;
    }
}
