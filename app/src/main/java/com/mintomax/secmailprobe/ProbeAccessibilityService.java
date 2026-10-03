package com.mintomax.secmailprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit-user-start Accessibility collector for Secmail.
 * v0.6 collection rule: read/unread does not matter; "processed vs unprocessed" does.
 * v0.6 UX: two-stage Discovery -> Collection with a small accessibility overlay.
 */
public class ProbeAccessibilityService extends AccessibilityService {

    public static final String PREFS = "probe_prefs";
    public static final String DEFAULT_TARGET = "com.uusafe.secmail";
    public static final String KEY_TARGET = "target_pkg";
    public static final String KEY_CAPTURE = "last_capture";
    public static final String KEY_CAPTURE_PKG = "last_capture_pkg";
    public static final String KEY_CAPTURE_TIME = "last_capture_time";
    public static final String KEY_NODE_COUNT = "last_node_count";
    public static final String KEY_EVENT = "last_event";
    public static final String KEY_SUBJECT = "mail_subject";
    public static final String KEY_SENDER = "mail_sender";
    public static final String KEY_RECIPIENTS = "mail_recipients";
    public static final String KEY_MAIL_DATE = "mail_date";
    public static final String KEY_BODY = "mail_body";

    public static final String KEY_BATCH_ACTIVE = "batch_active";
    public static final String KEY_BATCH_STATUS = "batch_status";
    public static final String KEY_BATCH_SAVED = "batch_saved";
    public static final String KEY_BATCH_OPENED = "batch_opened";
    public static final String KEY_BATCH_PROCESSED = "batch_processed";
    public static final String KEY_BATCH_SCROLLS = "batch_scrolls"; // compatibility
    public static final String KEY_BATCH_DUPES = "batch_dupes";
    public static final String KEY_BATCH_KNOWN = "batch_known";
    public static final String KEY_BATCH_LAST_FP = "batch_last_fp";
    public static final String KEY_BATCH_PENDING_LIST_SIG = "batch_pending_list_sig";
    public static final String KEY_BATCH_PENDING_PREVIEW = "batch_pending_preview";
    public static final String KEY_BATCH_PENDING_RETRY = "batch_pending_retry";
    public static final String KEY_BATCH_FAILED = "batch_failed";
    public static final String KEY_BATCH_FAILED_SIGS = "batch_failed_sigs";

    public static final String KEY_BATCH_PHASE = "batch_phase";
    public static final String KEY_BATCH_DISCOVERED_SIGS = "batch_discovered_sigs";
    public static final String KEY_BATCH_TARGET_SIGS = "batch_target_sigs";
    public static final String KEY_BATCH_DISCOVERED_TOTAL = "batch_discovered_total";
    public static final String KEY_BATCH_TARGET_TOTAL = "batch_target_total";
    public static final String KEY_BATCH_COMPLETED = "batch_completed";
    public static final String KEY_BATCH_DISCOVERY_SCROLLS = "batch_discovery_scrolls";
    public static final String KEY_BATCH_COLLECT_SCROLLS = "batch_collect_scrolls";
    public static final String KEY_BATCH_REWIND_REMAINING = "batch_rewind_remaining";
    public static final String KEY_BATCH_CURRENT_PREVIEW = "batch_current_preview";
    public static final String KEY_BATCH_STARTED_AT = "batch_started_at";

    private static final String PHASE_DISCOVER = "discover";
    private static final String PHASE_REWIND = "rewind";
    private static final String PHASE_COLLECT = "collect";
    private static final String PHASE_COMPLETE = "complete";

    private static final int MAX_NODES = 1000;
    private static final int MAX_CHARS = 260000;
    private static final int MAX_BATCH_OPENED = 100;
    private static final int MAX_BATCH_SCROLLS = 20;
    private static final int MAX_OPEN_RETRIES = 2;

    private long lastCaptureElapsed = 0L;
    private boolean automationBusy = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable batchTickRunnable = () -> runBatchTick();

    private WindowManager windowManager;
    private LinearLayout overlayRoot;
    private TextView overlayTitle;
    private TextView overlayDetail;
    private ProgressBar overlayProgress;
    private Button overlayActionButton;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (isBatchActive()) {
            showOrUpdateOverlay();
            scheduleBatchTick(500L);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!isBatchActive() && overlayRoot != null) removeOverlay();
        if (!isTargetPackage(pkg)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastCaptureElapsed < 300L) return;
        lastCaptureElapsed = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        StringBuilder out = new StringBuilder();
        int[] count = new int[]{0};
        walk(root, out, 0, count);

        MailSnapshot mail = extractMail(root);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        mail.capturedAt = timestamp;
        mail.packageName = pkg;

        SharedPreferences.Editor e = prefs().edit()
                .putString(KEY_CAPTURE, out.toString())
                .putString(KEY_CAPTURE_PKG, pkg)
                .putString(KEY_CAPTURE_TIME, timestamp)
                .putInt(KEY_NODE_COUNT, count[0])
                .putString(KEY_EVENT, AccessibilityEvent.eventTypeToString(event.getEventType()));

        if (mail.hasMailContent() && isDetailScreen(root)) {
            e.putString(KEY_SUBJECT, mail.subject)
                    .putString(KEY_SENDER, mail.sender)
                    .putString(KEY_RECIPIENTS, mail.recipients)
                    .putString(KEY_MAIL_DATE, mail.mailDate)
                    .putString(KEY_BODY, mail.body);
        }
        e.apply();

        if (isBatchActive()) {
            showOrUpdateOverlay();
            handleBatch(root, mail);
        } else if (overlayRoot != null) {
            removeOverlay();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void handleBatch(AccessibilityNodeInfo root, MailSnapshot mail) {
        if (automationBusy || !isBatchActive()) return;
        SharedPreferences p = prefs();
        int opened = p.getInt(KEY_BATCH_OPENED, 0);
        if (opened >= MAX_BATCH_OPENED) {
            stopBatch("완료: 안전 한도 " + MAX_BATCH_OPENED + "개 도달");
            return;
        }

        String phase = p.getString(KEY_BATCH_PHASE, PHASE_DISCOVER);
        if (isDetailScreen(root)) {
            if (PHASE_COLLECT.equals(phase)) handleDetailMail(mail, p);
            else returnToInboxFromUnexpectedDetail();
            return;
        }

        AccessibilityNodeInfo list = findBestInboxList(root);
        if (list == null) {
            AccessibilityNodeInfo inbox = findTextNode(root, new String[]{"받은편지함", "받은 메일함", "Inbox", "收件箱"});
            if (inbox != null && clickClosestClickable(inbox)) {
                setBatchStatus("Inbox로 이동 중…");
                pauseAndContinue(1200L);
            } else {
                setBatchStatus("Inbox 목록을 찾는 중… · Secmail에서 Inbox를 한 번 열어주세요");
                scheduleBatchTick(1200L);
            }
            return;
        }

        if (PHASE_REWIND.equals(phase)) {
            handleRewind(list, p);
        } else if (PHASE_COLLECT.equals(phase)) {
            handleCollect(list, p);
        } else {
            handleDiscovery(list, p);
        }
    }

    private void handleDiscovery(AccessibilityNodeInfo list, SharedPreferences p) {
        List<Candidate> candidates = extractCandidates(list);
        Set<String> discovered = readSet(KEY_BATCH_DISCOVERED_SIGS);
        Set<String> targets = readSet(KEY_BATCH_TARGET_SIGS);
        MailDbHelper db = new MailDbHelper(this);
        int known = p.getInt(KEY_BATCH_KNOWN, 0);

        for (Candidate c : candidates) {
            if (!discovered.add(c.signature)) continue;
            if (db.isCandidateProcessed(c.signature)) {
                db.touchCandidate(c.signature);
                known++;
            } else {
                targets.add(c.signature);
            }
        }

        writeSet(KEY_BATCH_DISCOVERED_SIGS, discovered);
        writeSet(KEY_BATCH_TARGET_SIGS, targets);
        p.edit()
                .putInt(KEY_BATCH_DISCOVERED_TOTAL, discovered.size())
                .putInt(KEY_BATCH_TARGET_TOTAL, targets.size())
                .putInt(KEY_BATCH_KNOWN, known)
                .putString(KEY_BATCH_STATUS, "Inbox 탐색 중 · " + discovered.size() + "개 확인 / 수집 대상 " + targets.size() + "개")
                .apply();
        showOrUpdateOverlay();

        int scrolls = p.getInt(KEY_BATCH_DISCOVERY_SCROLLS, 0);
        if (scrolls < MAX_BATCH_SCROLLS && list.isScrollable() && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            p.edit().putInt(KEY_BATCH_DISCOVERY_SCROLLS, scrolls + 1).apply();
            pauseAndContinue(900L);
            return;
        }

        p.edit()
                .putString(KEY_BATCH_PHASE, PHASE_REWIND)
                .putInt(KEY_BATCH_REWIND_REMAINING, scrolls)
                .putString(KEY_BATCH_STATUS, "수집 대상 " + targets.size() + "개 확정 · Inbox 처음으로 돌아가는 중…")
                .apply();
        showOrUpdateOverlay();
        handleRewind(list, p);
    }

    private void handleRewind(AccessibilityNodeInfo list, SharedPreferences p) {
        int remaining = p.getInt(KEY_BATCH_REWIND_REMAINING, 0);
        if (remaining > 0 && list.isScrollable() && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            int next = remaining - 1;
            p.edit()
                    .putInt(KEY_BATCH_REWIND_REMAINING, next)
                    .putString(KEY_BATCH_STATUS, "수집 준비 중 · Inbox 상단으로 이동 " + (p.getInt(KEY_BATCH_DISCOVERY_SCROLLS, 0) - next) + "/" + p.getInt(KEY_BATCH_DISCOVERY_SCROLLS, 0))
                    .apply();
            showOrUpdateOverlay();
            pauseAndContinue(850L);
            return;
        }

        p.edit()
                .putString(KEY_BATCH_PHASE, PHASE_COLLECT)
                .putInt(KEY_BATCH_COLLECT_SCROLLS, 0)
                .putString(KEY_BATCH_PROCESSED, "")
                .putInt(KEY_BATCH_PENDING_RETRY, 0)
                .putInt(KEY_BATCH_FAILED, 0)
                .putString(KEY_BATCH_FAILED_SIGS, "")
                .putString(KEY_BATCH_STATUS, "메일 수집 시작 · 0/" + p.getInt(KEY_BATCH_TARGET_TOTAL, 0))
                .apply();
        showOrUpdateOverlay();
        handleCollect(list, p);
    }

    private void handleCollect(AccessibilityNodeInfo list, SharedPreferences p) {
        int total = p.getInt(KEY_BATCH_TARGET_TOTAL, 0);
        int completed = p.getInt(KEY_BATCH_COMPLETED, 0);
        if (total <= 0) {
            stopBatch("완료: 새로 수집할 메일이 없습니다");
            return;
        }
        if (completed >= total) {
            int failed = p.getInt(KEY_BATCH_FAILED, 0);
            stopBatch("완료: 수집 대상 " + total + "개 처리 완료" + (failed > 0 ? " · 열기 실패 " + failed : ""));
            return;
        }

        Set<String> targets = readSet(KEY_BATCH_TARGET_SIGS);
        Set<String> sessionProcessed = readProcessed();
        Set<String> failedSigs = readSet(KEY_BATCH_FAILED_SIGS);
        MailDbHelper db = new MailDbHelper(this);
        List<Candidate> candidates = extractCandidates(list);

        // If a click previously reported success but we are still on the Inbox list,
        // the navigation did not complete. Retry with a conservative screen tap once/twice
        // instead of silently skipping that mail.
        String pendingSig = p.getString(KEY_BATCH_PENDING_LIST_SIG, "");
        if (!pendingSig.isEmpty()) {
            Candidate pending = findCandidate(candidates, pendingSig);
            int retry = p.getInt(KEY_BATCH_PENDING_RETRY, 0);
            if (pending != null && retry < MAX_OPEN_RETRIES) {
                boolean attempted = retry == 0 ? clickClosestClickable(pending.node) : tapCandidateCenter(pending.node);
                p.edit()
                        .putInt(KEY_BATCH_PENDING_RETRY, retry + 1)
                        .putString(KEY_BATCH_STATUS, "수집 " + (completed + 1) + "/" + total + " · 메일 열기 재시도 " + (retry + 1) + "/" + MAX_OPEN_RETRIES)
                        .apply();
                showOrUpdateOverlay();
                if (attempted) {
                    pauseAndContinue(1400L);
                    return;
                }
            }

            sessionProcessed.add(pendingSig);
            failedSigs.add(pendingSig);
            writeProcessed(sessionProcessed);
            writeSet(KEY_BATCH_FAILED_SIGS, failedSigs);
            int failed = p.getInt(KEY_BATCH_FAILED, 0) + 1;
            p.edit()
                    .putInt(KEY_BATCH_FAILED, failed)
                    .putInt(KEY_BATCH_COMPLETED, completed + 1)
                    .putInt(KEY_BATCH_PENDING_RETRY, 0)
                    .putString(KEY_BATCH_PENDING_LIST_SIG, "")
                    .putString(KEY_BATCH_PENDING_PREVIEW, "")
                    .putString(KEY_BATCH_STATUS, "메일 열기 실패 · 다음 메일 계속 · 실패 " + failed)
                    .apply();
            showOrUpdateOverlay();
            scheduleBatchTick(250L);
            return;
        }

        Candidate chosen = null;
        for (Candidate c : candidates) {
            if (sessionProcessed.contains(c.signature) || failedSigs.contains(c.signature)) continue;
            if (db.isCandidateProcessed(c.signature)) {
                sessionProcessed.add(c.signature);
                writeProcessed(sessionProcessed);
                continue;
            }
            // Prefer a signature discovered in stage 1, but accept a still-unprocessed
            // row if Secmail changed the row text slightly after read/unread rendering.
            if (targets.contains(c.signature)) {
                chosen = c;
                break;
            }
            if (chosen == null) chosen = c;
        }

        if (chosen != null) {
            if (!targets.contains(chosen.signature)) {
                targets.add(chosen.signature);
                writeSet(KEY_BATCH_TARGET_SIGS, targets);
                total = Math.max(total, targets.size());
                p.edit().putInt(KEY_BATCH_TARGET_TOTAL, total).apply();
            }
            boolean attempted = clickClosestClickable(chosen.node);
            if (!attempted) attempted = tapCandidateCenter(chosen.node);
            if (attempted) {
                int nextOpened = p.getInt(KEY_BATCH_OPENED, 0) + 1;
                p.edit()
                        .putInt(KEY_BATCH_OPENED, nextOpened)
                        .putString(KEY_BATCH_PENDING_LIST_SIG, chosen.signature)
                        .putString(KEY_BATCH_PENDING_PREVIEW, chosen.preview)
                        .putInt(KEY_BATCH_PENDING_RETRY, 0)
                        .putString(KEY_BATCH_CURRENT_PREVIEW, trim(chosen.preview, 80))
                        .putString(KEY_BATCH_STATUS, "수집 " + (completed + 1) + "/" + total + " · 메일 여는 중…")
                        .apply();
                showOrUpdateOverlay();
                pauseAndContinue(1500L);
                return;
            }

            sessionProcessed.add(chosen.signature);
            failedSigs.add(chosen.signature);
            writeProcessed(sessionProcessed);
            writeSet(KEY_BATCH_FAILED_SIGS, failedSigs);
            int failed = p.getInt(KEY_BATCH_FAILED, 0) + 1;
            p.edit()
                    .putInt(KEY_BATCH_FAILED, failed)
                    .putInt(KEY_BATCH_COMPLETED, completed + 1)
                    .putString(KEY_BATCH_STATUS, "메일 클릭 불가 · 다음 메일 계속 · 실패 " + failed)
                    .apply();
            showOrUpdateOverlay();
            scheduleBatchTick(250L);
            return;
        }

        int collectScrolls = p.getInt(KEY_BATCH_COLLECT_SCROLLS, 0);
        int scanScrolls = Math.max(1, p.getInt(KEY_BATCH_DISCOVERY_SCROLLS, 0));
        if (collectScrolls <= scanScrolls + 1 && collectScrolls < MAX_BATCH_SCROLLS && list.isScrollable() && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            p.edit()
                    .putInt(KEY_BATCH_COLLECT_SCROLLS, collectScrolls + 1)
                    .putString(KEY_BATCH_STATUS, "수집 " + completed + "/" + total + " · 다음 Inbox 구간 확인 중…")
                    .apply();
            showOrUpdateOverlay();
            pauseAndContinue(950L);
            return;
        }

        int failed = p.getInt(KEY_BATCH_FAILED, 0);
        stopBatch("완료: 접근 가능한 Inbox 수집 종료 · " + completed + "/" + total + (failed > 0 ? " · 열기 실패 " + failed : ""));
    }

    private void handleDetailMail(MailSnapshot mail, SharedPreferences p) {
        if (!mail.looksComplete()) {
            setBatchStatus("메일 본문 로딩 중…");
            pauseAndContinue(650L);
            return;
        }

        String pendingSig = p.getString(KEY_BATCH_PENDING_LIST_SIG, "");
        String pendingPreview = p.getString(KEY_BATCH_PENDING_PREVIEW, "");
        if (pendingSig.isEmpty()) {
            returnToInboxFromUnexpectedDetail();
            return;
        }

        MailDbHelper db = new MailDbHelper(this);
        ImportantInfoExtractor.Result info = ImportantInfoExtractor.extract(mail.subject, mail.body);
        String fp = MailDbHelper.fingerprintFor(mail);
        MailDbHelper.SaveResult sr = db.saveIfNew(mail, info, pendingSig, pendingPreview);

        Set<String> sessionProcessed = readProcessed();
        sessionProcessed.add(pendingSig);
        writeProcessed(sessionProcessed);

        int saved = p.getInt(KEY_BATCH_SAVED, 0) + (sr.inserted ? 1 : 0);
        int dupes = p.getInt(KEY_BATCH_DUPES, 0) + (sr.duplicate ? 1 : 0);
        int completed = p.getInt(KEY_BATCH_COMPLETED, 0) + 1;
        int total = p.getInt(KEY_BATCH_TARGET_TOTAL, 0);
        String result = sr.inserted ? (sr.threadUpdate ? "Thread 업데이트 저장" : "새 메일 저장") : "기존 메일 확인";

        p.edit()
                .putInt(KEY_BATCH_SAVED, saved)
                .putInt(KEY_BATCH_DUPES, dupes)
                .putInt(KEY_BATCH_COMPLETED, completed)
                .putString(KEY_BATCH_LAST_FP, fp)
                .putString(KEY_BATCH_PENDING_LIST_SIG, "")
                .putString(KEY_BATCH_PENDING_PREVIEW, "")
                .putInt(KEY_BATCH_PENDING_RETRY, 0)
                .putString(KEY_BATCH_STATUS, result + " · " + completed + "/" + total + " 완료")
                .apply();
        showOrUpdateOverlay();

        if (prefs().getBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, true) && sr.id > 0) {
            String latest = ImportantInfoExtractor.latestMessageBody(mail.body);
            TranslationHelper.translateAndStore(this, sr.id, latest, null);
        }

        automationBusy = true;
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            resumeAfterCurrentAction(1050L);
        }, 500L);
    }

    private void returnToInboxFromUnexpectedDetail() {
        automationBusy = true;
        setBatchStatus("현재 메일 상세 화면 → Inbox로 이동 중…");
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            resumeAfterCurrentAction(1050L);
        }, 350L);
    }

    private boolean isDetailScreen(AccessibilityNodeInfo root) {
        if (root == null) return false;
        boolean body = findByIdRecursive(root, "main-body") != null ||
                findByIdRecursive(root, "mailContentContainer") != null ||
                findByIdRecursive(root, "contentDiv") != null;
        boolean replyUi = findByIdRecursive(root, DEFAULT_TARGET + ":id/quick_reply_bar_input_edittext") != null ||
                findByIdRecursive(root, DEFAULT_TARGET + ":id/titlebar_reply_mail_btn") != null ||
                findByIdRecursive(root, DEFAULT_TARGET + ":id/titlebar_forward_mail_btn") != null;
        return body || replyUi;
    }

    private AccessibilityNodeInfo findBestInboxList(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> lists = new ArrayList<>();
        collectLists(root, lists);
        AccessibilityNodeInfo best = null;
        int bestScore = 0;
        for (AccessibilityNodeInfo n : lists) {
            if (looksLikeDetailContainer(n)) continue;
            List<Candidate> rows = extractCandidates(n);
            if (rows.size() < 2) continue;
            int score = rows.size() * 4;
            String id = safe(n.getViewIdResourceName()).toLowerCase(Locale.ROOT);
            if (id.contains("mail_list") || id.contains("inbox") || id.contains("mail")) score += 6;
            if (n.isScrollable()) score += 3;
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    private boolean looksLikeDetailContainer(AccessibilityNodeInfo n) {
        return findByIdRecursive(n, "main-body") != null ||
                findByIdRecursive(n, "mailContentContainer") != null ||
                findByIdRecursive(n, DEFAULT_TARGET + ":id/quick_reply_bar_input_edittext") != null ||
                containsClass(n, "android.webkit.WebView");
    }

    private boolean containsClass(AccessibilityNodeInfo node, String className) {
        if (node == null) return false;
        if (safe(node.getClassName()).equals(className)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (containsClass(node.getChild(i), className)) return true;
        return false;
    }

    private void collectLists(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        String cls = safe(node.getClassName());
        if (cls.contains("RecyclerView") || cls.contains("ListView")) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectLists(node.getChild(i), out);
    }

    private static class Candidate {
        final String signature;
        final String preview;
        final AccessibilityNodeInfo node;
        Candidate(String signature, String preview, AccessibilityNodeInfo node) {
            this.signature = signature;
            this.preview = preview;
            this.node = node;
        }
    }

    private List<Candidate> extractCandidates(AccessibilityNodeInfo list) {
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectCandidateNodes(list, 0, out, seen);
        return out;
    }

    private void collectCandidateNodes(AccessibilityNodeInfo node, int depth, List<Candidate> out, Set<String> seen) {
        if (node == null || depth > 5 || out.size() >= 30) return;
        if (depth > 0 && node.isClickable() && node.isEnabled()) {
            String compact = collectCompactText(node);
            int score = rowScore(node, compact);
            if (score >= 3) {
                String sig = MailDbHelper.listSignatureFor(compact);
                if (!seen.contains(sig)) {
                    seen.add(sig);
                    out.add(new Candidate(sig, compact, node));
                    return;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) collectCandidateNodes(node.getChild(i), depth + 1, out, seen);
    }

    private int rowScore(AccessibilityNodeInfo node, String compact) {
        if (compact == null) return 0;
        String x = compact.replaceAll("\\s+", " ").trim();
        if (x.length() < 5 || x.length() > 520) return 0;
        String low = x.toLowerCase(Locale.ROOT);
        if (low.equals("reply") || low.equals("forward") || low.contains("전체 회신") ||
                low.contains("caution: external email") || low.contains("the content of this e-mail is confidential")) return 0;

        int score = 1;
        if (descendantIdContains(node, "subject")) score += 2;
        if (descendantIdContains(node, "sender") || descendantIdContains(node, "from")) score += 2;
        if (descendantIdContains(node, "date") || descendantIdContains(node, "time")) score += 2;
        if (x.matches("(?s).*\\b\\d{1,2}:\\d{2}\\b.*") || x.matches("(?s).*\\b\\d{1,2}/\\d{1,2}\\b.*")) score += 1;
        if (x.length() >= 15 && x.length() <= 320) score += 1;
        return score;
    }

    private boolean descendantIdContains(AccessibilityNodeInfo node, String token) {
        if (node == null) return false;
        String id = safe(node.getViewIdResourceName()).toLowerCase(Locale.ROOT);
        if (id.contains(token)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (descendantIdContains(node.getChild(i), token)) return true;
        return false;
    }

    private AccessibilityNodeInfo findClickable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isClickable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = findClickable(node.getChild(i));
            if (c != null) return c;
        }
        return null;
    }

    private boolean clickClosestClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 6 && n != null; i++) {
            if (n.isClickable() && n.isEnabled() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            n = n.getParent();
        }
        AccessibilityNodeInfo c = findClickable(node);
        return c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private Candidate findCandidate(List<Candidate> candidates, String signature) {
        if (signature == null || signature.isEmpty() || candidates == null) return null;
        for (Candidate c : candidates) if (signature.equals(c.signature)) return c;
        return null;
    }

    /**
     * Gesture fallback used only after ACTION_CLICK did not navigate away from a row.
     * Bounds come from a row already accepted by the conservative Inbox candidate filter.
     */
    private boolean tapCandidateCenter(AccessibilityNodeInfo node) {
        if (node == null || !node.isEnabled()) return false;
        try {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.width() < dp(16) || r.height() < dp(16)) return false;
            float x = r.exactCenterX();
            float y = r.exactCenterY();
            if (y < dp(88)) return false; // do not tap through the Assistant overlay/app bar area
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0L, 80L);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void pauseAndContinue(long delayMs) {
        automationBusy = true;
        handler.removeCallbacks(batchTickRunnable);
        handler.postDelayed(() -> {
            automationBusy = false;
            runBatchTick();
        }, Math.max(150L, delayMs));
    }

    private void resumeAfterCurrentAction(long delayMs) {
        handler.removeCallbacks(batchTickRunnable);
        handler.postDelayed(() -> {
            automationBusy = false;
            runBatchTick();
        }, Math.max(150L, delayMs));
    }

    private void scheduleBatchTick(long delayMs) {
        handler.removeCallbacks(batchTickRunnable);
        handler.postDelayed(batchTickRunnable, Math.max(100L, delayMs));
    }

    /**
     * Explicit continuation makes the state machine independent from Secmail emitting a
     * second AccessibilityEvent after scroll/click/back. v0.6 could stall at 0/N because
     * the only event often arrived while automationBusy was true and was then discarded.
     */
    private void runBatchTick() {
        if (!isBatchActive() || automationBusy) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            setBatchStatus("Secmail 화면 응답 대기 중…");
            scheduleBatchTick(800L);
            return;
        }
        String pkg = safe(root.getPackageName());
        if (!isTargetPackage(pkg)) {
            setBatchStatus("Secmail 화면으로 돌아오면 수집을 계속합니다");
            scheduleBatchTick(1000L);
            return;
        }
        MailSnapshot mail = extractMail(root);
        mail.capturedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        mail.packageName = pkg;
        handleBatch(root, mail);
    }

    private AccessibilityNodeInfo findTextNode(AccessibilityNodeInfo node, String[] targets) {
        if (node == null) return null;
        String t = safe(node.getText()).trim();
        for (String target : targets) if (t.equalsIgnoreCase(target)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo f = findTextNode(node.getChild(i), targets);
            if (f != null) return f;
        }
        return null;
    }

    private Set<String> readProcessed() { return readSet(KEY_BATCH_PROCESSED); }
    private void writeProcessed(Set<String> set) { writeSet(KEY_BATCH_PROCESSED, set); }

    private Set<String> readSet(String key) {
        String raw = prefs().getString(key, "");
        Set<String> s = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return s;
        for (String x : raw.split("\\n")) if (!x.trim().isEmpty()) s.add(x.trim());
        return s;
    }

    private void writeSet(String key, Set<String> set) {
        StringBuilder b = new StringBuilder();
        for (String x : set) {
            if (b.length() > 0) b.append('\n');
            b.append(x);
            if (b.length() > 24000) break;
        }
        prefs().edit().putString(key, b.toString()).apply();
    }

    private boolean isBatchActive() { return prefs().getBoolean(KEY_BATCH_ACTIVE, false); }

    private void setBatchStatus(String status) {
        prefs().edit().putString(KEY_BATCH_STATUS, status).apply();
        showOrUpdateOverlay();
    }

    private void stopBatch(String reason) {
        prefs().edit()
                .putBoolean(KEY_BATCH_ACTIVE, false)
                .putString(KEY_BATCH_PHASE, PHASE_COMPLETE)
                .putString(KEY_BATCH_STATUS, reason)
                .putString(KEY_BATCH_PENDING_LIST_SIG, "")
                .putString(KEY_BATCH_PENDING_PREVIEW, "")
                .putInt(KEY_BATCH_PENDING_RETRY, 0)
                .apply();
        handler.removeCallbacks(batchTickRunnable);
        automationBusy = false;
        showOrUpdateOverlay();
        handler.postDelayed(this::removeOverlay, 12000L);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void showOrUpdateOverlay() {
        SharedPreferences p = prefs();
        boolean active = p.getBoolean(KEY_BATCH_ACTIVE, false);
        if (overlayRoot == null) {
            try {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                overlayRoot = new LinearLayout(this);
                overlayRoot.setOrientation(LinearLayout.VERTICAL);
                overlayRoot.setPadding(dp(14), dp(10), dp(10), dp(10));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.argb(242, 15, 23, 42));
                bg.setCornerRadius(dp(12));
                overlayRoot.setBackground(bg);

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                overlayTitle = new TextView(this);
                overlayTitle.setTextColor(Color.WHITE);
                overlayTitle.setTextSize(14);
                overlayTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                overlayDetail = new TextView(this);
                overlayDetail.setTextColor(Color.rgb(203, 213, 225));
                overlayDetail.setTextSize(11);
                texts.addView(overlayTitle);
                texts.addView(overlayDetail);
                top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
                overlayActionButton = new Button(this);
                overlayActionButton.setText("중지");
                overlayActionButton.setAllCaps(false);
                overlayActionButton.setTextSize(11);
                overlayActionButton.setOnClickListener(v -> {
                    if (isBatchActive()) stopBatch("사용자 중지");
                    else openActionInbox();
                });
                top.addView(overlayActionButton, new LinearLayout.LayoutParams(dp(96), dp(42)));
                overlayRoot.addView(top);

                overlayProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                overlayProgress.setMax(100);
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(5));
                pp.setMargins(0, dp(7), 0, 0);
                overlayRoot.addView(overlayProgress, pp);

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP;
                lp.x = 0;
                lp.y = dp(8);
                windowManager.addView(overlayRoot, lp);
            } catch (Exception e) {
                overlayRoot = null;
                return;
            }
        }

        String phase = p.getString(KEY_BATCH_PHASE, PHASE_DISCOVER);
        int discovered = p.getInt(KEY_BATCH_DISCOVERED_TOTAL, 0);
        int total = p.getInt(KEY_BATCH_TARGET_TOTAL, 0);
        int completed = p.getInt(KEY_BATCH_COMPLETED, 0);
        int saved = p.getInt(KEY_BATCH_SAVED, 0);
        int known = p.getInt(KEY_BATCH_KNOWN, 0);
        int failed = p.getInt(KEY_BATCH_FAILED, 0);
        String status = p.getString(KEY_BATCH_STATUS, "대기 중");
        String preview = p.getString(KEY_BATCH_CURRENT_PREVIEW, "");

        if (!active) {
            long startedAt = p.getLong(KEY_BATCH_STARTED_AT, 0L);
            MailDbHelper db = new MailDbHelper(this);
            int reply = db.countStatusSince("reply", startedAt);
            int action = db.countStatusSince("action", startedAt);
            int schedule = db.countStatusSince("schedule", startedAt);
            int fyi = db.countStatusSince("fyi", startedAt);
            overlayTitle.setText("Secmail Assistant · 수집 완료 → Action 확인");
            overlayDetail.setText("신규 " + saved + " · 회신 " + reply + " · Action " + action + " · 일정 " + schedule + " · FYI " + fyi +
                    (failed > 0 ? " · 열기 실패 " + failed : "") + "\n" + status);
            overlayProgress.setIndeterminate(false);
            overlayProgress.setProgress(100);
            if (overlayActionButton != null) overlayActionButton.setText((reply + action) > 0 ? "Action Inbox" : "결과 보기");
            return;
        }
        if (overlayActionButton != null) overlayActionButton.setText("중지");

        if (PHASE_DISCOVER.equals(phase) || PHASE_REWIND.equals(phase)) {
            overlayTitle.setText("Secmail Assistant · Inbox 탐색 중");
            overlayDetail.setText("Inbox 확인 " + discovered + " · 수집 대상 " + total + " · 기존처리 " + known + "\n" + status);
            overlayProgress.setIndeterminate(true);
        } else {
            overlayTitle.setText("Secmail Assistant · 수집 " + completed + " / " + total);
            overlayDetail.setText((preview.isEmpty() ? status : trim(preview, 68)) + "\n새 저장 " + saved + " · 실패 " + failed + " · " + status);
            overlayProgress.setIndeterminate(false);
            int progress = total <= 0 ? 0 : Math.min(100, Math.round((completed * 100f) / total));
            overlayProgress.setProgress(progress);
        }
    }

    private void openActionInbox() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("open_action_inbox", true);
            startActivity(i);
        } catch (Exception ignored) {}
        removeOverlay();
    }

    private void removeOverlay() {
        if (overlayRoot == null || windowManager == null) return;
        try { windowManager.removeView(overlayRoot); } catch (Exception ignored) {}
        overlayRoot = null;
        overlayTitle = null;
        overlayDetail = null;
        overlayProgress = null;
        overlayActionButton = null;
    }

    private MailSnapshot extractMail(AccessibilityNodeInfo root) {
        MailSnapshot m = new MailSnapshot();
        m.subject = firstTextById(root, DEFAULT_TARGET + ":id/mail_subject_textview");
        m.recipients = firstTextById(root, DEFAULT_TARGET + ":id/to_area_general_tv");
        m.mailDate = firstTextById(root, DEFAULT_TARGET + ":id/mail_date_textview");
        m.sender = findSender(root);

        AccessibilityNodeInfo bodyNode = findByIdRecursive(root, "main-body");
        if (bodyNode == null) bodyNode = findByIdRecursive(root, "mailContentContainer");
        if (bodyNode == null) bodyNode = findByIdRecursive(root, "contentDiv");
        if (bodyNode != null) m.body = collectUsefulText(bodyNode);
        return m;
    }

    private String firstTextById(AccessibilityNodeInfo root, String id) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    String t = safe(n.getText()).trim();
                    if (!t.isEmpty()) return t;
                }
            }
        } catch (Exception ignored) {}
        AccessibilityNodeInfo n = findByIdRecursive(root, id);
        return n == null ? "" : safe(n.getText()).trim();
    }

    private String findSender(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findByIdRecursive(root, DEFAULT_TARGET + ":id/mail_sender_name_tv");
        if (label == null) return "";
        AccessibilityNodeInfo parent = label.getParent();
        if (parent == null) return "";
        boolean afterLabel = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo c = parent.getChild(i);
            if (c == null) continue;
            if (c.equals(label)) { afterLabel = true; continue; }
            if (!afterLabel) continue;
            String id = safe(c.getViewIdResourceName());
            String t = safe(c.getText()).trim();
            if (t.isEmpty()) continue;
            if (id.endsWith("mail_date_textview") || id.endsWith("to_area_general_tv")) continue;
            if (t.equals("발신") || t.equals("발신 ")) continue;
            return t;
        }
        return "";
    }

    private AccessibilityNodeInfo findByIdRecursive(AccessibilityNodeInfo node, String wanted) {
        if (node == null) return null;
        String id = safe(node.getViewIdResourceName());
        if (id.equals(wanted) || id.endsWith(":" + wanted) || id.endsWith("/" + wanted)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByIdRecursive(node.getChild(i), wanted);
            if (found != null) return found;
        }
        return null;
    }

    private String collectUsefulText(AccessibilityNodeInfo node) {
        Set<String> ordered = new LinkedHashSet<>();
        collectText(node, ordered);
        StringBuilder b = new StringBuilder();
        for (String s : ordered) {
            String x = s.replace('\u00A0', ' ').replace("\\n", "\n").trim();
            if (x.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(x);
        }
        return b.toString().trim();
    }

    private String collectCompactText(AccessibilityNodeInfo node) {
        Set<String> ordered = new LinkedHashSet<>();
        collectText(node, ordered);
        StringBuilder b = new StringBuilder();
        for (String s : ordered) {
            String x = s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
            if (x.isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(x);
            if (b.length() > 520) break;
        }
        return b.toString();
    }

    private void collectText(AccessibilityNodeInfo node, Set<String> out) {
        if (node == null) return;
        String t = safe(node.getText()).trim();
        if (!t.isEmpty() && node.getChildCount() == 0) out.add(t);
        for (int i = 0; i < node.getChildCount(); i++) collectText(node.getChild(i), out);
    }

    private boolean isTargetPackage(String pkg) {
        if (getPackageName().equals(pkg)) return false;
        SharedPreferences p = prefs();
        String target = p.getString(KEY_TARGET, DEFAULT_TARGET);
        if (target != null && !target.trim().isEmpty()) return target.trim().equals(pkg);
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            String label = pm.getApplicationLabel(ai).toString();
            return label.toLowerCase(Locale.ROOT).contains("secmail");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void walk(AccessibilityNodeInfo node, StringBuilder out, int depth, int[] count) {
        if (node == null || count[0] >= MAX_NODES || out.length() >= MAX_CHARS) return;
        count[0]++;
        String text = safe(node.getText());
        String desc = safe(node.getContentDescription());
        String cls = safe(node.getClassName());
        String id = safe(node.getViewIdResourceName());
        out.append('[').append(count[0]).append("] depth=").append(depth)
                .append(" class=").append(cls)
                .append(" id=").append(id)
                .append(" clickable=").append(node.isClickable())
                .append(" scrollable=").append(node.isScrollable())
                .append(" enabled=").append(node.isEnabled());
        if (!text.isEmpty()) out.append(" text=\"").append(clean(text)).append('"');
        if (!desc.isEmpty()) out.append(" desc=\"").append(clean(desc)).append('"');
        out.append('\n');
        for (int i = 0; i < node.getChildCount(); i++) {
            if (count[0] >= MAX_NODES || out.length() >= MAX_CHARS) break;
            walk(node.getChild(i), out, depth + 1, count);
        }
    }

    private String safe(CharSequence s) { return s == null ? "" : s.toString(); }
    private String clean(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", "\\n");
    }
    private String trim(String s, int max) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= max ? x : x.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }
}
