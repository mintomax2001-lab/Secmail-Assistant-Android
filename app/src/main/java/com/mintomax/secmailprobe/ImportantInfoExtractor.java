package com.mintomax.secmailprobe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local-only rule extractor. It intentionally produces candidates, not authoritative facts.
 */
public final class ImportantInfoExtractor {
    private ImportantInfoExtractor() {}

    public static class Result {
        public final List<String> keyPoints = new ArrayList<>();
        public final List<String> actions = new ArrayList<>();
        public final List<String> schedules = new ArrayList<>();
        public final List<String> topicTags = new ArrayList<>();
        public int importanceScore = 0;
        public String importanceLabel = "기타";
        public String workflowStatus = "fyi";
    }

    private static final Pattern[] DATE_PATTERNS = new Pattern[]{
            Pattern.compile("\\b\\d{1,2}/\\d{1,2}\\s*(?:~|–|-)\\s*\\d{1,2}/\\d{1,2}\\b"),
            Pattern.compile("\\b20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}\\b"),
            Pattern.compile("\\b\\d{1,2}월\\s*\\d{1,2}일\\b"),
            Pattern.compile("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}(?:,\\s*20\\d{2})?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b\\d{1,2}:\\d{2}\\b")
    };

    private static final String[] ACTION_WORDS = new String[]{
            "요청", "부탁", "바랍니다", "해주세요", "필요", "검토", "확인", "회신", "선정", "결정", "승인", "조치", "일정",
            "please", "request", "need", "review", "confirm", "reply", "approve", "approval", "provide", "schedule", "action", "feedback",
            "请", "需要", "确认", "安排", "回复", "提供", "批准", "申请"
    };

    private static final String[] REPLY_WORDS = new String[]{
            "회신", "답변", "알려", "확인 부탁", "의견 부탁", "피드백", "언제", "가능한지",
            "reply", "respond", "response", "please confirm", "please provide", "please review", "let us know", "do let us know",
            "may i know", "could you", "would you", "can you", "when will", "feedback", "get back to", "revert",
            "回复", "请确认", "请提供", "反馈", "什么时候", "能否"
    };

    private static final String[] IMPORTANT_WORDS = new String[]{
            "긴급", "중요", "asap", "urgent", "deadline", "due", "important", "즉시", "尽快", "重要", "紧急"
    };

    private static final String[] BUSINESS_WORDS = new String[]{
            "pdk", "nto", "mpw", "voc", "customer", "고객", "客户", "approval", "승인", "request", "요청", "申请",
            "foundry", "process", "tool", "technology transfer", "cost", "com", "pricing", "price", "capacity", "yield", "quality",
            "roadmap", "nda", "automotive", "proposal", "meeting", "visit"
    };

    private static final String[][] TAG_RULES = new String[][]{
            {"PDK", "pdk"},
            {"NTO/MPW", "nto", "mpw", "tape-out", "tapeout"},
            {"Process/Tool", "process", "tool", "equipment", "공정", "장비", "工艺", "设备"},
            {"Customer/VOC", "customer", "voc", "고객", "客户"},
            {"Pricing/CoM", "price", "pricing", "cost", "com", "wafer price", "가격", "원가", "报价", "成本"},
            {"Capacity", "capacity", "cap", "캐파", "产能"},
            {"Quality/Yield", "quality", "yield", "reliability", "수율", "품질", "良率", "质量"},
            {"Meeting/Visit", "meeting", "visit", "미팅", "방문", "会议", "拜访"},
            {"Roadmap/Proposal", "roadmap", "proposal", "business model", "合作", "提案"}
    };

    public static Result extract(String subject, String body) {
        Result r = new Result();
        String cleanSubject = cleanSubject(subject);
        String normalized = latestMessageBody(body);
        List<String> sentences = splitSentences(normalized);

        Set<String> scheduleSet = new LinkedHashSet<>();
        for (Pattern p : DATE_PATTERNS) {
            Matcher m = p.matcher(normalized);
            while (m.find() && scheduleSet.size() < 4) scheduleSet.add(clean(m.group()));
        }
        r.schedules.addAll(scheduleSet);

        Set<String> actions = new LinkedHashSet<>();
        for (String s : sentences) {
            if (isNoise(s) || looksLikeHeader(s)) continue;
            if (containsAny(s, ACTION_WORDS) || s.contains("?")) actions.add(trimForCard(s, 180));
            if (actions.size() >= 4) break;
        }
        r.actions.addAll(actions);

        Set<String> keys = new LinkedHashSet<>();
        for (String s : sentences) {
            if (isNoise(s) || looksLikeHeader(s)) continue;
            boolean useful = containsDate(s) || containsAny(s, ACTION_WORDS) || containsAny(s, BUSINESS_WORDS) || s.length() >= 22;
            if (useful) keys.add(trimForCard(s, 180));
            if (keys.size() >= 4) break;
        }
        if (keys.isEmpty() && !cleanSubject.isEmpty()) keys.add(trimForCard(cleanSubject, 150));
        r.keyPoints.addAll(keys);

        String combined = (cleanSubject + " " + normalized).toLowerCase(Locale.ROOT);
        for (String[] rule : TAG_RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (combined.contains(rule[i].toLowerCase(Locale.ROOT))) {
                    r.topicTags.add(rule[0]);
                    break;
                }
            }
            if (r.topicTags.size() >= 4) break;
        }

        int score = 0;
        if (!r.actions.isEmpty()) score += 2;
        if (!r.schedules.isEmpty()) score += 1;
        if (containsAny(combined, IMPORTANT_WORDS)) score += 2;
        if (containsAny(combined, BUSINESS_WORDS)) score += 1;
        r.importanceScore = Math.min(score, 5);
        r.importanceLabel = r.importanceScore >= 3 ? "중요" : (r.importanceScore >= 1 ? "확인" : "기타");

        boolean replyNeeded = combined.contains("?") || containsAny(combined, REPLY_WORDS);
        if (replyNeeded) r.workflowStatus = "reply";
        else if (!r.actions.isEmpty()) r.workflowStatus = "action";
        else if (!r.schedules.isEmpty()) r.workflowStatus = "schedule";
        else r.workflowStatus = "fyi";
        return r;
    }

    public static String latestMessageBody(String body) {
        String raw = body == null ? "" : body.replace('\u00A0', ' ').replace("\\n", "\n");
        return stripQuotedHistory(raw);
    }

    public static String cleanSubject(String subject) {
        String s = clean(subject);
        String prev;
        do {
            prev = s;
            s = s.replaceFirst("(?i)^(re|fw|fwd)\\s*:\\s*", "")
                    .replaceFirst("^(回复|答复|转发|回覆)\\s*[:：]\\s*", "");
        } while (!s.equals(prev));
        return s;
    }

    public static String threadKey(String subject) {
        String s = cleanSubject(subject).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String stripQuotedHistory(String body) {
        String s = body == null ? "" : body.replace("\r", "").trim();
        if (s.isEmpty()) return s;
        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        int meaningful = 0;
        for (String raw : lines) {
            String line = clean(raw);
            if (line.isEmpty()) {
                if (out.length() > 0) out.append('\n');
                continue;
            }
            if (meaningful > 0 && isQuotedBoundary(line)) break;
            if (looksLikeHeader(line) && meaningful > 0) break;
            if (isExternalWarning(line)) continue;
            out.append(line).append('\n');
            if (!isNoise(line)) meaningful++;
        }
        String result = out.toString().trim();
        return cutCommonSignature(result);
    }

    private static String cutCommonSignature(String s) {
        if (s == null || s.isEmpty()) return "";
        String low = s.toLowerCase(Locale.ROOT);
        String[] markers = new String[]{
                "\nthks,", "\nthanks,", "\nbest regards", "\nbest regard", "\n감사합니다.", "\n감사합니다",
                "\nthe content of this e-mail is confidential", "\n此邮件可能包含机密信息"
        };
        int cut = s.length();
        for (String marker : markers) {
            int i = low.indexOf(marker.toLowerCase(Locale.ROOT));
            if (i > 30 && i < cut) cut = i;
        }
        return s.substring(0, cut).trim();
    }

    private static boolean isExternalWarning(String s) {
        String low = clean(s).toLowerCase(Locale.ROOT);
        return low.startsWith("caution: external email") ||
                low.startsWith("please take care when clicking links") ||
                low.startsWith("[external] this message comes from an external organization");
    }

    private static boolean isQuotedBoundary(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        return low.startsWith("-----original message") || low.startsWith("from:") || low.startsWith("sent:") ||
                low.startsWith("subject:") || low.startsWith("to:") || s.startsWith("发件人：") || s.startsWith("发件人:") ||
                s.startsWith("发送时间：") || s.startsWith("发送时间:") || s.startsWith("主题：") || s.startsWith("主题:") ||
                s.startsWith("收件人：") || s.startsWith("收件人:");
    }

    private static boolean looksLikeHeader(String s) {
        String low = clean(s).toLowerCase(Locale.ROOT);
        return low.startsWith("from:") || low.startsWith("sent:") || low.startsWith("to:") || low.startsWith("subject:") ||
                low.startsWith("cc:") || s.startsWith("发件人") || s.startsWith("发送时间") || s.startsWith("收件人") ||
                s.startsWith("抄送") || s.startsWith("主题");
    }

    private static boolean containsDate(String s) {
        for (Pattern p : DATE_PATTERNS) if (p.matcher(s).find()) return true;
        return false;
    }

    private static boolean containsAny(String s, String[] words) {
        String low = s == null ? "" : s.toLowerCase(Locale.ROOT);
        for (String w : words) if (low.contains(w.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static List<String> splitSentences(String body) {
        List<String> out = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) return out;
        String[] chunks = body.split("(?<=[.!?。！？])\\s+|\\n+");
        for (String c : chunks) {
            String x = clean(c);
            if (!x.isEmpty()) out.add(x);
        }
        return out;
    }

    private static boolean isNoise(String s) {
        String x = clean(s);
        if (x.isEmpty()) return true;
        String low = x.toLowerCase(Locale.ROOT);
        return low.equals("안녕하세요.") || low.equals("안녕하세요") || low.equals("감사합니다.") || low.equals("감사합니다") ||
                low.equals("thanks") || low.equals("thank you") || low.equals("thnaks.") || x.length() <= 2;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String trimForCard(String s, int max) {
        String x = clean(s);
        return x.length() <= max ? x : x.substring(0, max - 1) + "…";
    }
}
