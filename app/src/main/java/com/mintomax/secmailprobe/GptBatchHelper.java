package com.mintomax.secmailprobe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class GptBatchHelper {
    private GptBatchHelper() {}

    public static final String PREF_GPT_OVERVIEW = "gpt_batch_overview";
    public static final String PREF_GPT_OVERVIEW_TIME = "gpt_batch_overview_time";

    public static File createMailPack(Context context, List<SavedMailRecord> records, String scopeLabel) throws Exception {
        JSONArray items = new JSONArray();
        for (SavedMailRecord r : records) {
            JSONObject o = new JSONObject();
            o.put("id", r.fingerprint);
            o.put("date", r.mailDate);
            o.put("sender", r.sender);
            o.put("recipients", r.recipients);
            o.put("subject", ImportantInfoExtractor.cleanSubject(r.subject));
            String latest = r.latestBody == null || r.latestBody.trim().isEmpty()
                    ? ImportantInfoExtractor.latestMessageBody(r.body) : r.latestBody;
            if (latest.length() > 5000) latest = latest.substring(0, 5000) + "…";
            o.put("latest_message", latest);
            o.put("local_tags", r.topicTags);
            o.put("local_workflow", r.workflowStatus);
            o.put("importance", r.importance);
            o.put("thread_key", r.threadKey);
            o.put("existing_ai_summary", r.aiSummary);
            items.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("schema_version", "mobile-mail-pack-v1");
        root.put("app_version", "0.7");
        root.put("scope", scopeLabel == null ? "" : scopeLabel);
        root.put("mail_count", items.length());
        root.put("generated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));
        root.put("items", items);

        File dir = new File(context.getCacheDir(), "gpt_share");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("GPT 공유 폴더 생성 실패");
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(dir, "Secmail_MAIL_PACK_" + ts + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    public static String buildBatchPrompt(int mailCount, String scopeLabel) {
        return "첨부한 Secmail MAIL_PACK JSON을 분석해줘.\n\n" +
                "목적: 메일을 단순 요약하지 말고, 사용자가 지금 무엇을 알아야 하고 무엇을 해야 하는지 Action Inbox 형태로 정리한다.\n" +
                "분석 범위: " + scopeLabel + " / " + mailCount + "건\n\n" +
                "업무 프로필: 반도체/Foundry/Fabless/BCD/PMIC/LDMOS/HV Device/PDK/MPW/NTO/tape-out/wafer/yield/reliability/qualification/FA/ESD/SOA/CoM/pricing/capacity/VOC/GDS/lot/split 등의 전문 용어 의미를 보존한다.\n" +
                "원문에 없는 공정 capability, 일정, 가격, 수율, 고객 commitment, owner, due date, 기술 결과를 만들지 말고 불확실하면 [확인 필요]로 표시한다.\n" +
                "같은 thread/중복 내용은 가능한 한 묶어서 판단하되, items 결과는 입력 id별로 각각 반환한다.\n\n" +
                "분류 기준:\n" +
                "- reply: 상대방이 답변/확인/의사결정을 기대함\n" +
                "- action: 내부 확인/조치/협업이 먼저 필요함\n" +
                "- schedule: 미팅/방문/마감/일정 처리가 핵심임\n" +
                "- fyi: 즉시 행동이 합리적으로 필요하지 않음\n" +
                "- P1: 긴급 deadline, 고객/생산 blocking, quality/reliability risk, 큰 business risk에 한정\n\n" +
                "회신이 필요한 메일은 상대방 메일 언어(한국어/영어/중국어)를 우선해 실제로 보낼 수 있는 간결하고 전문적인 추천 회신 초안을 작성한다.\n" +
                "웹 확인이 실제로 필요하면 공개 회사명/제품명/기술용어만 검색하고 메일의 비공개 내용, 이메일 주소, 내부 가격/일정/캐파, 첨부 내용은 검색어에 넣지 않는다.\n\n" +
                "JSON ONLY로 아래 schema를 정확히 반환해줘. Markdown code fence는 쓰지 마.\n" +
                "{\n" +
                "  \"overview_ko\":\"최근 메일 전체를 5~8문장으로 요약\",\n" +
                "  \"top_actions\":[{\"priority\":\"P1|P2|P3\",\"subject\":\"제목\",\"action\":\"사용자가 해야 할 일\"}],\n" +
                "  \"reply_highlights\":[{\"subject\":\"제목\",\"why\":\"왜 회신이 필요한지\"}],\n" +
                "  \"know_now\":[\"반드시 알아둘 핵심 사실/흐름\"],\n" +
                "  \"risks\":[\"리스크 또는 [확인 필요] 항목\"],\n" +
                "  \"items\":[{\n" +
                "    \"id\":\"MAIL_PACK의 정확한 id\",\n" +
                "    \"class\":\"reply|action|schedule|fyi\",\n" +
                "    \"priority\":\"P1|P2|P3\",\n" +
                "    \"summary_ko\":\"1~2문장 핵심 요약\",\n" +
                "    \"next_action_ko\":\"구체적인 다음 행동\",\n" +
                "    \"reason_ko\":\"판단 근거 및 확인 필요 사항\",\n" +
                "    \"reply_draft\":\"회신 필요 시 바로 보낼 수 있는 추천 답변. 불필요하면 빈 문자열\",\n" +
                "    \"reply_language\":\"ko|en|zh|other\",\n" +
                "    \"confidence\":0.0\n" +
                "  }]\n" +
                "}\n" +
                "모든 items는 입력 MAIL_PACK의 id를 그대로 유지해야 한다.";
    }

    public static void sharePack(Activity activity, File pack, String prompt) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", pack);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, prompt);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setPackage("com.openai.chatgpt");
        try {
            activity.startActivity(send);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("application/json");
            fallback.putExtra(Intent.EXTRA_STREAM, uri);
            fallback.putExtra(Intent.EXTRA_TEXT, prompt);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(fallback, "MAIL_PACK 분석에 사용할 앱 선택"));
        }
    }

    public static String clipboardText(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return "";
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence s = clip.getItemAt(0).coerceToText(context);
        return s == null ? "" : s.toString();
    }

    public static void copyText(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Secmail Assistant", text == null ? "" : text));
    }

    public static String cleanJson(String source) {
        String x = source == null ? "" : source.trim();
        x = x.replaceFirst("(?is)^```(?:json)?\\s*", "");
        x = x.replaceFirst("(?is)\\s*```$", "");
        int a = x.indexOf('{');
        int b = x.lastIndexOf('}');
        if (a >= 0 && b > a) return x.substring(a, b + 1);
        return x;
    }

    public static ImportResult importJson(MailDbHelper db, String rawJson) throws Exception {
        JSONObject root = new JSONObject(cleanJson(rawJson));
        JSONArray arr;
        if (root.has("items")) arr = root.optJSONArray("items");
        else {
            arr = new JSONArray();
            arr.put(root);
        }
        int applied = 0;
        int missing = 0;
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.optJSONObject(i);
                if (x == null) continue;
                int n = db.updateGptAnalysis(
                        x.optString("id", ""),
                        x.optString("class", ""),
                        x.optString("priority", ""),
                        x.optString("summary_ko", ""),
                        x.optString("next_action_ko", ""),
                        x.optString("reason_ko", ""),
                        x.optString("reply_draft", ""),
                        x.optString("reply_language", ""),
                        x.optDouble("confidence", 0.0)
                );
                if (n > 0) applied++; else missing++;
            }
        }
        String overview = root.optString("overview_ko", "");
        return new ImportResult(applied, missing, overview);
    }

    public static final class ImportResult {
        public final int applied;
        public final int missing;
        public final String overview;
        ImportResult(int applied, int missing, String overview) {
            this.applied = applied;
            this.missing = missing;
            this.overview = overview == null ? "" : overview;
        }
    }
}
