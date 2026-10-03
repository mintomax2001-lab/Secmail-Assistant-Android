package com.mintomax.secmailprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private MailDbHelper db;
    private TextView serviceStatus, targetStatus, subjectView, metaView, toView, bodyView;
    private TextView keyView, actionView, scheduleView, savedCountView, debugView;
    private TextView batchStatusView, savedSummaryView, translationStatusView, translationView, postSyncSummaryView, gptBatchSummaryView;
    private LinearLayout savedListContainer;
    private ScrollView mainScroll;
    private View actionInboxCard;
    private EditText targetBox, searchBox;
    private MailSnapshot currentMail;
    private ImportantInfoExtractor.Result currentInfo;
    private boolean bodyExpanded = false;
    private boolean debugExpanded = false;
    private String currentFilter = "all";
    private String currentQuery = "";
    private String currentSavedTranslation = "";

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(20, 29, 43));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(2), 0, dp(5));
        return v;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        if (primary) {
            b.setBackgroundColor(Color.rgb(37, 99, 235));
            b.setTextColor(Color.WHITE);
        }
        return b;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        l.setLayoutParams(lp);
        return l;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(229, 234, 242));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ProbeAccessibilityService.PREFS, Context.MODE_PRIVATE);
        db = new MailDbHelper(this);
        if (prefs.getString(ProbeAccessibilityService.KEY_TARGET, "").trim().isEmpty()) {
            prefs.edit().putString(ProbeAccessibilityService.KEY_TARGET, ProbeAccessibilityService.DEFAULT_TARGET).apply();
        }

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setBackgroundColor(Color.rgb(244, 247, 252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(36));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(12));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        header.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        ht.addView(text("Secmail Assistant", 23, true));
        TextView vs = text("v0.7 · Batch GPT Pack + Result Import", 12, false);
        vs.setTextColor(Color.rgb(99, 110, 128));
        ht.addView(vs);
        header.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        LinearLayout status = card();
        status.addView(text("연결 상태", 16, true));
        serviceStatus = text("", 13, true);
        targetStatus = text("", 12, false);
        status.addView(serviceStatus);
        status.addView(targetStatus);
        LinearLayout statusBtns = new LinearLayout(this);
        Button access = button("접근성 설정", false);
        Button open = button("Secmail 열기", true);
        statusBtns.addView(access, new LinearLayout.LayoutParams(0, dp(50), 1f));
        statusBtns.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1f));
        status.addView(statusBtns);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        open.setOnClickListener(v -> openSecmail());
        root.addView(status);

        LinearLayout batch = card();
        batch.addView(text("Inbox 동기화", 17, true));
        TextView batchNote = text("읽음/안읽음과 관계없이 아직 처리하지 않은 Inbox 메일을 확인합니다. 먼저 목록을 탐색해 수집 대상 개수를 계산한 뒤, Secmail 화면 위 진행 Overlay에서 n/N 상태를 보여주며 수집합니다.", 11, false);
        batchNote.setTextColor(Color.rgb(99, 110, 128));
        batch.addView(batchNote);
        batchStatusView = text("대기 중", 13, true);
        batch.addView(batchStatusView);
        LinearLayout batchBtns = new LinearLayout(this);
        Button batchStart = button("Inbox 동기화", true);
        Button batchStop = button("중지", false);
        batchBtns.addView(batchStart, new LinearLayout.LayoutParams(0, dp(52), 1.7f));
        batchBtns.addView(batchStop, new LinearLayout.LayoutParams(0, dp(52), 0.7f));
        batch.addView(batchBtns);
        batchStart.setOnClickListener(v -> confirmStartBatch());
        batchStop.setOnClickListener(v -> stopBatch("사용자 중지"));
        root.addView(batch);

        LinearLayout next = card();
        next.addView(text("수집 후 처리", 17, true));
        TextView nextNote = text("수집은 끝이 아니라 시작입니다. 앱이 자동으로 분류/번역/Action 후보를 만들고, 사용자는 회신·내부조치·일정·대기 여부만 결정합니다.", 11, false);
        nextNote.setTextColor(Color.rgb(99, 110, 128));
        next.addView(nextNote);
        postSyncSummaryView = text("동기화 후 처리할 업무를 여기에 정리합니다.", 13, true);
        next.addView(postSyncSummaryView);
        Button goActionInbox = button("지금 처리할 메일 보기", true);
        next.addView(goActionInbox, new LinearLayout.LayoutParams(-1, dp(50)));
        goActionInbox.setOnClickListener(v -> {
            currentFilter = "attention";
            currentQuery = "";
            if (searchBox != null) searchBox.setText("");
            renderSavedList();
            scrollToActionInbox();
        });
        root.addView(next);

        LinearLayout gptBatch = card();
        gptBatch.addView(text("GPT 전체분석 · NO API", 17, true));
        TextView gptNote = text("수집된 메일을 MAIL_PACK.json으로 묶어 ChatGPT 앱에 명시적으로 공유합니다. GPT가 전체 맥락을 보고 Reply / Action / Schedule / FYI, 우선순위, 요약, 다음 행동, 추천 회신을 정리합니다. 결과 JSON을 복사해 다시 가져오면 Action Inbox에 반영됩니다.", 11, false);
        gptNote.setTextColor(Color.rgb(99, 110, 128));
        gptBatch.addView(gptNote);
        gptBatchSummaryView = text("GPT 분석 상태 확인 중…", 12, true);
        gptBatch.addView(gptBatchSummaryView);
        LinearLayout gptBtns = new LinearLayout(this);
        Button makePack = button("GPT 분석팩 만들기", true);
        Button importGpt = button("GPT 결과 가져오기", false);
        gptBtns.addView(makePack, new LinearLayout.LayoutParams(0, dp(52), 1.2f));
        gptBtns.addView(importGpt, new LinearLayout.LayoutParams(0, dp(52), 1f));
        gptBatch.addView(gptBtns);
        makePack.setOnClickListener(v -> chooseGptPackScope());
        importGpt.setOnClickListener(v -> showGptImportDialog());
        TextView gptPrivacy = text("공유 범위는 사용자가 직접 선택합니다. 회사 정책상 외부 AI 공유가 허용되는 메일에만 사용하세요. 첨부파일은 자동 포함하지 않습니다.", 10, false);
        gptPrivacy.setTextColor(Color.rgb(99, 110, 128));
        gptBatch.addView(gptPrivacy);
        root.addView(gptBatch);

        LinearLayout translateCard = card();
        translateCard.addView(text("외국어 자동 번역", 17, true));
        TextView translateNote = text("영어/중국어 등 외국어 최신 메시지를 자동 감지해 한국어로 번역합니다. 원문은 그대로 보존하고 분류/중복판정은 원문 기준입니다. 번역 입력 텍스트는 ML Kit에서 기기 내 처리되며, 최초 언어 모델 다운로드에는 인터넷이 필요할 수 있습니다.", 11, false);
        translateNote.setTextColor(Color.rgb(99, 110, 128));
        translateCard.addView(translateNote);
        translationStatusView = text("", 12, true);
        translateCard.addView(translationStatusView);
        LinearLayout transBtns = new LinearLayout(this);
        Button autoTranslate = button("", true);
        Button prepareModels = button("영/중 모델 준비", false);
        transBtns.addView(autoTranslate, new LinearLayout.LayoutParams(0, dp(50), 1.2f));
        transBtns.addView(prepareModels, new LinearLayout.LayoutParams(0, dp(50), 1f));
        translateCard.addView(transBtns);
        TextView attribution = text("자동 번역 · Google Translate · 기계 번역은 기술 용어/의미가 부정확할 수 있어 원문이 기준입니다.", 10, false);
        attribution.setTextColor(Color.rgb(99, 110, 128));
        translateCard.addView(attribution);
        Runnable refreshTranslateButton = () -> {
            boolean on = prefs.getBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, true);
            autoTranslate.setText(on ? "자동 번역 ON" : "자동 번역 OFF");
            translationStatusView.setText(on ? "외국어 메일 저장 후 자동 번역 활성화" : "자동 번역 비활성화");
        };
        refreshTranslateButton.run();
        autoTranslate.setOnClickListener(v -> {
            boolean enabled = !prefs.getBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, true);
            prefs.edit().putBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, enabled).apply();
            refreshTranslateButton.run();
        });
        prepareModels.setOnClickListener(v -> TranslationHelper.prepareEnglishChineseModels(this, new TranslationHelper.PrepareCallback() {
            @Override public void onProgress(String status) { runOnUiThread(() -> translationStatusView.setText(status)); }
            @Override public void onComplete(boolean ok, String status) { runOnUiThread(() -> { translationStatusView.setText(status); if (ok) toast("번역 모델 준비 완료"); }); }
        }));
        root.addView(translateCard);

        LinearLayout mail = card();
        mail.addView(text("최근 읽은 메일", 17, true));
        subjectView = text("아직 읽은 메일이 없습니다.", 19, true);
        metaView = text("", 12, false);
        toView = text("", 12, false);
        bodyView = text("", 14, false);
        bodyView.setTextIsSelectable(true);
        mail.addView(subjectView);
        mail.addView(metaView);
        mail.addView(toView);
        mail.addView(divider());
        mail.addView(bodyView);
        translationView = text("", 13, false);
        translationView.setTextColor(Color.rgb(30, 64, 175));
        translationView.setVisibility(View.GONE);
        mail.addView(translationView);
        LinearLayout mailBtns = new LinearLayout(this);
        Button refresh = button("현재 메일 읽기", true);
        Button save = button("저장", false);
        Button original = button("원문 보기", false);
        mailBtns.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1.3f));
        mailBtns.addView(save, new LinearLayout.LayoutParams(0, dp(50), 0.8f));
        mailBtns.addView(original, new LinearLayout.LayoutParams(0, dp(50), 1f));
        mail.addView(mailBtns);
        refresh.setOnClickListener(v -> loadMail());
        save.setOnClickListener(v -> saveCurrentMail());
        original.setOnClickListener(v -> { bodyExpanded = !bodyExpanded; renderMail(); });
        root.addView(mail);

        LinearLayout insights = card();
        insights.addView(text("중요정보 · 로컬 추출", 17, true));
        TextView localNote = text("AI 미사용 · 현재 메일의 요청/행동/일정/업무 키워드를 휴대폰 안에서만 추출", 11, false);
        localNote.setTextColor(Color.rgb(99, 110, 128));
        insights.addView(localNote);
        insights.addView(text("핵심", 13, true));
        keyView = text("—", 13, false);
        insights.addView(keyView);
        insights.addView(text("Action", 13, true));
        actionView = text("—", 13, false);
        insights.addView(actionView);
        insights.addView(text("일정", 13, true));
        scheduleView = text("—", 13, false);
        insights.addView(scheduleView);
        savedCountView = text("로컬 저장 0건", 11, false);
        savedCountView.setTextColor(Color.rgb(99, 110, 128));
        insights.addView(savedCountView);
        root.addView(insights);

        LinearLayout saved = card();
        actionInboxCard = saved;
        saved.addView(text("Action Inbox", 18, true));
        TextView savedNote = text("메일 원문보다 '내가 무엇을 해야 하는지'를 먼저 봅니다. 자동 분류는 후보이며 상태는 직접 바꿀 수 있습니다.", 11, false);
        savedNote.setTextColor(Color.rgb(99, 110, 128));
        saved.addView(savedNote);
        savedSummaryView = text("", 11, false);
        savedSummaryView.setTextColor(Color.rgb(99, 110, 128));
        saved.addView(savedSummaryView);

        LinearLayout searchRow = new LinearLayout(this);
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("고객 / 제목 / 키워드 검색");
        Button searchBtn = button("검색", true);
        Button clearSearch = button("초기화", false);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, dp(50), 1.8f));
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(0, dp(50), 0.8f));
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(0, dp(50), 0.8f));
        saved.addView(searchRow);
        searchBtn.setOnClickListener(v -> { currentQuery = searchBox.getText().toString().trim(); renderSavedList(); });
        clearSearch.setOnClickListener(v -> { currentQuery = ""; searchBox.setText(""); renderSavedList(); });

        LinearLayout filters1 = new LinearLayout(this);
        Button all = button("전체", true);
        Button attention = button("회신/Action", false);
        Button waiting = button("대기", false);
        filters1.addView(all, new LinearLayout.LayoutParams(0, dp(46), 1f));
        filters1.addView(attention, new LinearLayout.LayoutParams(0, dp(46), 1.2f));
        filters1.addView(waiting, new LinearLayout.LayoutParams(0, dp(46), 1f));
        saved.addView(filters1);
        LinearLayout filters2 = new LinearLayout(this);
        Button schedule = button("일정", false);
        Button fyi = button("FYI", false);
        Button done = button("완료", false);
        filters2.addView(schedule, new LinearLayout.LayoutParams(0, dp(46), 1f));
        filters2.addView(fyi, new LinearLayout.LayoutParams(0, dp(46), 1f));
        filters2.addView(done, new LinearLayout.LayoutParams(0, dp(46), 1f));
        saved.addView(filters2);
        all.setOnClickListener(v -> { currentFilter = "all"; renderSavedList(); });
        attention.setOnClickListener(v -> { currentFilter = "attention"; renderSavedList(); });
        waiting.setOnClickListener(v -> { currentFilter = "waiting"; renderSavedList(); });
        schedule.setOnClickListener(v -> { currentFilter = "schedule"; renderSavedList(); });
        fyi.setOnClickListener(v -> { currentFilter = "fyi"; renderSavedList(); });
        done.setOnClickListener(v -> { currentFilter = "done"; renderSavedList(); });

        Button deleteAll = button("저장 메일 전체 삭제", false);
        saved.addView(deleteAll, new LinearLayout.LayoutParams(-1, dp(44)));
        deleteAll.setOnClickListener(v -> confirmDeleteAll());
        savedListContainer = new LinearLayout(this);
        savedListContainer.setOrientation(LinearLayout.VERTICAL);
        savedListContainer.setPadding(0, dp(10), 0, 0);
        saved.addView(savedListContainer);
        root.addView(saved);

        LinearLayout advanced = card();
        advanced.addView(text("고급 / Debug", 15, true));
        Button debug = button("UI-tree Debug 보기/숨기기", false);
        advanced.addView(debug);
        debugView = text("", 10, false);
        debugView.setTypeface(Typeface.MONOSPACE);
        debugView.setTextIsSelectable(true);
        debugView.setVisibility(View.GONE);
        advanced.addView(debugView);
        debug.setOnClickListener(v -> {
            debugExpanded = !debugExpanded;
            debugView.setVisibility(debugExpanded ? View.VISIBLE : View.GONE);
            if (debugExpanded) debugView.setText(prefs.getString(ProbeAccessibilityService.KEY_CAPTURE, "캡처 없음"));
        });
        targetBox = new EditText(this);
        targetBox.setSingleLine(true);
        targetBox.setText(prefs.getString(ProbeAccessibilityService.KEY_TARGET, ProbeAccessibilityService.DEFAULT_TARGET));
        advanced.addView(targetBox);
        Button targetSave = button("Target 저장", false);
        advanced.addView(targetSave);
        targetSave.setOnClickListener(v -> saveTarget());
        Button resetIndex = button("수집 이력 초기화", false);
        advanced.addView(resetIndex);
        resetIndex.setOnClickListener(v -> confirmResetCollectionIndex());
        TextView scope = text("기본 로컬 처리 · OCR 없음 · 메일 서버 직접 접속 없음 · Inbox 자동수집은 사용자가 명시적으로 시작한 동안만 수행 · 읽음/안읽음은 수집 기준이 아님 · 자동 번역은 최신 메시지만 기기 내 처리하며 모델 다운로드/SDK 진단을 위해 네트워크를 사용할 수 있음 · GPT 단건 검토 또는 사용자가 선택한 Batch 범위만 명시적으로 공유 · Batch 결과는 JSON Import 후 로컬 DB에 반영", 11, false);
        scope.setTextColor(Color.rgb(99, 110, 128));
        advanced.addView(scope);
        root.addView(advanced);

        setContentView(scroll);
        refreshStatus();
        loadMail();
        renderSavedList();
        if (getIntent() != null && getIntent().getBooleanExtra("open_action_inbox", false)) {
            currentFilter = "attention";
            renderSavedList();
            scrollToActionInbox();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        loadMail();
        renderSavedList();
    }

    private void refreshStatus() {
        serviceStatus.setText(isAccessibilityEnabled() ? "● Accessibility ON" : "○ Accessibility OFF");
        String target = prefs.getString(ProbeAccessibilityService.KEY_TARGET, ProbeAccessibilityService.DEFAULT_TARGET);
        targetStatus.setText("Target: " + target);
        if (targetBox != null) targetBox.setText(target);
        if (savedCountView != null) savedCountView.setText("로컬 저장 " + db.count() + "건");
        refreshBatchStatus();
        refreshPostSyncSummary();
        refreshGptBatchStatus();
    }

    private void refreshPostSyncSummary() {
        if (postSyncSummaryView == null || db == null) return;
        boolean active = prefs.getBoolean(ProbeAccessibilityService.KEY_BATCH_ACTIVE, false);
        long startedAt = prefs.getLong(ProbeAccessibilityService.KEY_BATCH_STARTED_AT, 0L);
        int saved = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_SAVED, 0);
        String phase = prefs.getString(ProbeAccessibilityService.KEY_BATCH_PHASE, "");
        if (active) {
            postSyncSummaryView.setText("동기화 진행 중 · 완료되면 회신/Action/일정/FYI로 자동 정리합니다.");
            return;
        }
        if (startedAt <= 0L || !"complete".equals(phase)) {
            int attention = db.countFilter("attention");
            int waiting = db.countFilter("waiting");
            postSyncSummaryView.setText("현재 처리 대기 · 회신/Action " + attention + " · 대기 " + waiting + "\n추천 흐름: 회신/Action → 일정 → FYI");
            return;
        }
        int reply = db.countStatusSince("reply", startedAt);
        int action = db.countStatusSince("action", startedAt);
        int schedule = db.countStatusSince("schedule", startedAt);
        int fyi = db.countStatusSince("fyi", startedAt);
        if (saved <= 0) {
            postSyncSummaryView.setText("최근 동기화 · 새 저장 메일 없음\n기존 Action Inbox에서 미완료 항목을 계속 처리하세요.");
        } else {
            postSyncSummaryView.setText("최근 동기화 · 신규 " + saved + "건 → 회신 " + reply + " · Action " + action + " · 일정 " + schedule + " · FYI " + fyi +
                    "\n추천: 회신/Action부터 처리하고, 발송 후에는 '대기 중'으로 변경");
        }
    }

    private void scrollToActionInbox() {
        if (mainScroll == null || actionInboxCard == null) return;
        mainScroll.post(() -> mainScroll.smoothScrollTo(0, Math.max(0, actionInboxCard.getTop() - dp(8))));
    }

    private void refreshBatchStatus() {
        if (batchStatusView == null) return;
        boolean active = prefs.getBoolean(ProbeAccessibilityService.KEY_BATCH_ACTIVE, false);
        String phase = prefs.getString(ProbeAccessibilityService.KEY_BATCH_PHASE, "discover");
        int discovered = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_DISCOVERED_TOTAL, 0);
        int total = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_TARGET_TOTAL, 0);
        int completed = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_COMPLETED, 0);
        int saved = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_SAVED, 0);
        int known = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_KNOWN, 0);
        int failed = prefs.getInt(ProbeAccessibilityService.KEY_BATCH_FAILED, 0);
        String status = prefs.getString(ProbeAccessibilityService.KEY_BATCH_STATUS, "대기 중");
        String phaseLabel = "discover".equals(phase) ? "Inbox 탐색" : ("rewind".equals(phase) ? "수집 준비" : ("collect".equals(phase) ? "메일 수집" : "완료"));
        batchStatusView.setText((active ? "● 실행 중 · " : "○ ") + phaseLabel + "\n" +
                "Inbox 확인 " + discovered + " · 수집 대상 " + total + " · 완료 " + completed + "/" + total + "\n" +
                "새 저장 " + saved + " · 기존처리 " + known + " · 열기 실패 " + failed + "\n" + status);
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName cn = new ComponentName(this, ProbeAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String s = splitter.next();
            if (s.equalsIgnoreCase(cn.flattenToString()) || s.equalsIgnoreCase(cn.flattenToShortString())) return true;
        }
        return false;
    }

    private void confirmStartBatch() {
        new AlertDialog.Builder(this)
                .setTitle("Inbox 동기화")
                .setMessage("읽음/안읽음과 관계없이 아직 처리하지 않은 Inbox 메일을 확인합니다.\n\n1) Inbox 목록 탐색 → 수집 대상 개수 계산\n2) Secmail 화면 위 Progress Overlay로 n/N 표시\n3) 미처리 메일만 열어 저장\n\n최대 100개 메일 / 20개 목록 구간까지 동작합니다. 메일 열기 실패 시 자동 재시도 후 다음 메일로 계속합니다. 진행할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("시작", (d, w) -> startBatch())
                .show();
    }

    private void startBatch() {
        if (!isAccessibilityEnabled()) {
            toast("먼저 Accessibility Service를 ON 해주세요.");
            return;
        }
        prefs.edit()
                .putBoolean(ProbeAccessibilityService.KEY_BATCH_ACTIVE, true)
                .putLong(ProbeAccessibilityService.KEY_BATCH_STARTED_AT, System.currentTimeMillis())
                .putString(ProbeAccessibilityService.KEY_BATCH_STATUS, "Secmail 실행 중…")
                .putInt(ProbeAccessibilityService.KEY_BATCH_SAVED, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_OPENED, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_SCROLLS, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_DUPES, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_KNOWN, 0)
                .putString(ProbeAccessibilityService.KEY_BATCH_PHASE, "discover")
                .putString(ProbeAccessibilityService.KEY_BATCH_DISCOVERED_SIGS, "")
                .putString(ProbeAccessibilityService.KEY_BATCH_TARGET_SIGS, "")
                .putInt(ProbeAccessibilityService.KEY_BATCH_DISCOVERED_TOTAL, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_TARGET_TOTAL, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_COMPLETED, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_DISCOVERY_SCROLLS, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_COLLECT_SCROLLS, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_REWIND_REMAINING, 0)
                .putString(ProbeAccessibilityService.KEY_BATCH_CURRENT_PREVIEW, "")
                .putString(ProbeAccessibilityService.KEY_BATCH_PROCESSED, "")
                .putString(ProbeAccessibilityService.KEY_BATCH_LAST_FP, "")
                .putString(ProbeAccessibilityService.KEY_BATCH_PENDING_LIST_SIG, "")
                .putString(ProbeAccessibilityService.KEY_BATCH_PENDING_PREVIEW, "")
                .putInt(ProbeAccessibilityService.KEY_BATCH_PENDING_RETRY, 0)
                .putInt(ProbeAccessibilityService.KEY_BATCH_FAILED, 0)
                .putString(ProbeAccessibilityService.KEY_BATCH_FAILED_SIGS, "")
                .apply();
        refreshBatchStatus();
        openSecmail();
    }

    private void stopBatch(String reason) {
        prefs.edit()
                .putBoolean(ProbeAccessibilityService.KEY_BATCH_ACTIVE, false)
                .putString(ProbeAccessibilityService.KEY_BATCH_STATUS, reason)
                .apply();
        refreshBatchStatus();
        renderSavedList();
    }

    private void openSecmail() {
        String pkg = prefs.getString(ProbeAccessibilityService.KEY_TARGET, ProbeAccessibilityService.DEFAULT_TARGET).trim();
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            discoverSecmail();
            pkg = prefs.getString(ProbeAccessibilityService.KEY_TARGET, ProbeAccessibilityService.DEFAULT_TARGET).trim();
            launch = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (launch == null) { toast("Secmail 실행 Activity를 찾지 못했습니다."); return; }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void discoverSecmail() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            String l = label == null ? "" : label.toString().toLowerCase(Locale.ROOT);
            if (pkg.equals(ProbeAccessibilityService.DEFAULT_TARGET) || l.contains("secmail")) {
                prefs.edit().putString(ProbeAccessibilityService.KEY_TARGET, pkg).apply();
                return;
            }
        }
    }

    private void saveTarget() {
        String pkg = targetBox.getText().toString().trim();
        if (pkg.isEmpty()) { toast("Package name을 입력하세요."); return; }
        if (pkg.equals(getPackageName())) { toast("Reader 자체 package는 Target으로 사용할 수 없습니다."); return; }
        prefs.edit().putString(ProbeAccessibilityService.KEY_TARGET, pkg).apply();
        refreshStatus();
        toast("Target 저장 완료");
    }

    private void loadMail() {
        MailSnapshot m = new MailSnapshot();
        m.subject = prefs.getString(ProbeAccessibilityService.KEY_SUBJECT, "");
        m.sender = prefs.getString(ProbeAccessibilityService.KEY_SENDER, "");
        m.recipients = prefs.getString(ProbeAccessibilityService.KEY_RECIPIENTS, "");
        m.mailDate = prefs.getString(ProbeAccessibilityService.KEY_MAIL_DATE, "");
        m.body = prefs.getString(ProbeAccessibilityService.KEY_BODY, "");
        m.capturedAt = prefs.getString(ProbeAccessibilityService.KEY_CAPTURE_TIME, "");
        m.packageName = prefs.getString(ProbeAccessibilityService.KEY_CAPTURE_PKG, "");
        currentMail = m;
        currentSavedTranslation = "";
        currentInfo = ImportantInfoExtractor.extract(m.subject, m.body);
        renderMail();
        if (debugExpanded) debugView.setText(prefs.getString(ProbeAccessibilityService.KEY_CAPTURE, "캡처 없음"));
    }

    private void renderMail() {
        if (currentMail == null || !currentMail.hasMailContent()) {
            subjectView.setText("아직 읽은 메일이 없습니다.");
            metaView.setText("Secmail에서 메일을 열거나 Inbox 일괄 확인을 시작하세요.");
            toView.setText("");
            bodyView.setText("");
            if (translationView != null) { translationView.setVisibility(View.GONE); translationView.setText(""); }
            keyView.setText("—"); actionView.setText("—"); scheduleView.setText("—");
            return;
        }
        String cleanSubject = ImportantInfoExtractor.cleanSubject(currentMail.subject);
        subjectView.setText(empty(cleanSubject, "(제목 없음)"));
        String priority = currentInfo == null ? "" : " · " + currentInfo.importanceLabel + " " + currentInfo.importanceScore;
        metaView.setText("From: " + empty(currentMail.sender, "—") + "   ·   " + empty(currentMail.mailDate, "시간 미확인") + priority);
        toView.setText("To: " + empty(currentMail.recipients, "—"));
        String body = currentMail.body == null ? "" : currentMail.body;
        if (!bodyExpanded && body.length() > 420) body = body.substring(0, 420) + "…";
        bodyView.setText(body);
        if (translationView != null) {
            if (currentSavedTranslation != null && !currentSavedTranslation.trim().isEmpty()) {
                translationView.setVisibility(View.VISIBLE);
                translationView.setText("\n[한국어 자동 번역 · Google Translate]\n" + currentSavedTranslation);
            } else {
                translationView.setVisibility(View.GONE);
                translationView.setText("");
            }
        }
        keyView.setText(bullets(currentInfo.keyPoints));
        actionView.setText(bullets(currentInfo.actions));
        scheduleView.setText(bullets(currentInfo.schedules));
    }

    private void saveCurrentMail() {
        if (currentMail == null || !currentMail.hasMailContent()) { toast("먼저 메일을 읽어오세요."); return; }
        MailDbHelper.SaveResult sr = db.saveIfNew(currentMail, currentInfo);
        if (sr.inserted) toast("로컬 저장 완료");
        else if (sr.duplicate) toast("이미 저장된 메일입니다.");
        else toast("저장 실패");
        if (sr.id > 0 && prefs.getBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, true)) {
            String latest = ImportantInfoExtractor.latestMessageBody(currentMail.body);
            TranslationHelper.translateAndStore(this, sr.id, latest, (lang, translated, status) -> runOnUiThread(() -> {
                if (!translated.trim().isEmpty()) { currentSavedTranslation = translated; renderMail(); renderSavedList(); }
            }));
        }
        refreshStatus();
        renderSavedList();
    }

    private void renderSavedList() {
        if (savedListContainer == null) return;
        savedListContainer.removeAllViews();
        int all = db.countFilter("all");
        int attention = db.countFilter("attention");
        int waiting = db.countFilter("waiting");
        int schedule = db.countFilter("schedule");
        int fyi = db.countFilter("fyi");
        int gptAnalyzed = db.countGptAnalyzed();
        savedSummaryView.setText("전체 " + all + " · 회신/Action " + attention + " · 대기 " + waiting + " · 일정 " + schedule + " · FYI " + fyi + " · GPT분석 " + gptAnalyzed);
        refreshPostSyncSummary();
        refreshGptBatchStatus();
        List<SavedMailRecord> records = db.list(currentFilter, currentQuery, 200);
        if (records.isEmpty()) {
            savedListContainer.addView(text(currentQuery.isEmpty() ? "해당 조건의 저장 메일이 없습니다." : "검색 결과가 없습니다.", 12, false));
            return;
        }
        for (SavedMailRecord r : records) addSavedRow(r);
    }

    private void addSavedRow(SavedMailRecord r) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(2), dp(10), dp(2), dp(10));
        String badge = "[" + workflowLabel(r.workflowStatus) + "] ";
        if (r.importance >= 3) badge = "[중요] " + badge;
        TextView title = text(badge + empty(ImportantInfoExtractor.cleanSubject(r.subject), "(제목 없음)"), 14, true);
        item.addView(title);
        TextView meta = text(empty(r.sender, "발신자 미확인") + " · " + empty(r.mailDate, formatSavedAt(r.savedAt)), 11, false);
        meta.setTextColor(Color.rgb(99, 110, 128));
        item.addView(meta);
        if (!r.topicTags.trim().isEmpty()) {
            TextView tags = text("Tags · " + r.topicTags.replace("\n", " · "), 11, false);
            tags.setTextColor(Color.rgb(61, 91, 145));
            item.addView(tags);
        }
        if (!r.aiSummary.trim().isEmpty()) {
            String pri = r.aiPriority.trim().isEmpty() ? "" : r.aiPriority + " · ";
            TextView ai = text("GPT · " + pri + trim(r.aiSummary, 190), 12, true);
            ai.setTextColor(Color.rgb(91, 33, 182));
            item.addView(ai);
            if (!r.aiNextAction.trim().isEmpty()) {
                TextView aiNext = text("추천 행동 · " + trim(r.aiNextAction, 170), 11, false);
                aiNext.setTextColor(Color.rgb(67, 56, 202));
                item.addView(aiNext);
            }
        }
        if (!r.translatedBody.trim().isEmpty()) {
            TextView tr = text("한국어 번역 · " + trim(r.translatedBody, 190), 12, false);
            tr.setTextColor(Color.rgb(30, 64, 175));
            item.addView(tr);
            TextView attr = text("Google Translate · 기계 번역 참고용", 9, false);
            attr.setTextColor(Color.rgb(99, 110, 128));
            item.addView(attr);
        }
        String preview = firstNonEmptyLine(r.keyPoints);
        if (!preview.isEmpty()) item.addView(text("핵심 · " + trim(preview, 160), 12, false));
        if (!r.actions.trim().isEmpty()) item.addView(text("Action · " + trim(firstNonEmptyLine(r.actions), 160), 12, false));
        if (!r.schedules.trim().isEmpty()) item.addView(text("일정 · " + trim(firstNonEmptyLine(r.schedules), 160), 12, false));
        TextView nextAction = text("다음 행동 · " + recommendedNextAction(r), 11, true);
        nextAction.setTextColor(Color.rgb(30, 64, 175));
        item.addView(nextAction);

        LinearLayout rowBtns = new LinearLayout(this);
        Button view = button("보기", false);
        Button gpt = button(!r.aiReplyDraft.trim().isEmpty() ? "추천 회신" : ("reply".equals(r.workflowStatus) ? "회신 준비" : "GPT 검토"), true);
        Button status = button("상태", false);
        Button delete = button("삭제", false);
        rowBtns.addView(view, new LinearLayout.LayoutParams(0, dp(44), 0.8f));
        rowBtns.addView(gpt, new LinearLayout.LayoutParams(0, dp(44), 1.1f));
        rowBtns.addView(status, new LinearLayout.LayoutParams(0, dp(44), 0.8f));
        rowBtns.addView(delete, new LinearLayout.LayoutParams(0, dp(44), 0.7f));
        item.addView(rowBtns);
        view.setOnClickListener(v -> showSavedRecord(r));
        gpt.setOnClickListener(v -> {
            if (!r.aiReplyDraft.trim().isEmpty()) showRecommendedReply(r);
            else confirmGptReview(r);
        });
        status.setOnClickListener(v -> chooseWorkflowStatus(r));
        delete.setOnClickListener(v -> confirmDelete(r));
        savedListContainer.addView(item);
        savedListContainer.addView(divider());
    }

    private String recommendedNextAction(SavedMailRecord r) {
        if (r != null && r.aiNextAction != null && !r.aiNextAction.trim().isEmpty()) return r.aiNextAction.trim();
        String status = r == null ? "" : r.workflowStatus;
        if ("reply".equals(status)) return "상대 요청 확인 → 회신 준비 → Secmail 발송 후 '대기 중'으로 변경";
        if ("action".equals(status)) return "내부 확인/조치 → 결과 확보 → 고객 회신 필요 여부 판단";
        if ("schedule".equals(status)) return "날짜/시간 원문 확인 → 일정 등록 또는 담당자 확인";
        if ("waiting".equals(status)) return "상대/내부 회신 대기 → 새 회신 도착 시 다시 검토";
        if ("done".equals(status)) return "추가 조치 없음";
        return "정보 확인 → 후속조치 없으면 완료 처리";
    }

    private String workflowLabel(String status) {
        if ("reply".equals(status)) return "회신 필요";
        if ("action".equals(status)) return "Action";
        if ("waiting".equals(status)) return "대기 중";
        if ("schedule".equals(status)) return "일정";
        if ("done".equals(status)) return "완료";
        return "FYI";
    }

    private void chooseWorkflowStatus(SavedMailRecord r) {
        String[] labels = new String[]{"회신 필요", "내부 Action", "대기 중", "일정", "FYI", "완료"};
        String[] values = new String[]{"reply", "action", "waiting", "schedule", "fyi", "done"};
        new AlertDialog.Builder(this)
                .setTitle("업무 상태 변경")
                .setItems(labels, (d, which) -> {
                    int n = db.updateWorkflowStatus(r.id, values[which]);
                    if (n > 0) toast("상태 변경: " + labels[which]);
                    renderSavedList();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmDelete(SavedMailRecord r) {
        new AlertDialog.Builder(this)
                .setTitle("저장 메일 삭제")
                .setMessage("이 메일을 로컬 저장함에서 삭제할까요?\n\n" + trim(ImportantInfoExtractor.cleanSubject(r.subject), 100) + "\n\n※ 수집 이력은 남겨 다음 자동 수집 때 같은 메일을 다시 가져오지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> {
                    int n = db.deleteById(r.id);
                    if (n > 0) toast("삭제 완료"); else toast("삭제할 메일을 찾지 못했습니다.");
                    refreshStatus();
                    renderSavedList();
                })
                .show();
    }

    private void confirmDeleteAll() {
        int count = db.count();
        if (count <= 0) { toast("삭제할 저장 메일이 없습니다."); return; }
        new AlertDialog.Builder(this)
                .setTitle("저장 메일 전체 삭제")
                .setMessage("로컬 저장 메일 " + count + "건을 모두 삭제합니다. 수집 이력은 유지되므로 같은 메일이 자동으로 다시 들어오지는 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("전체 삭제", (d, w) -> {
                    int n = db.deleteAll();
                    toast("로컬 저장 " + n + "건 삭제 완료");
                    refreshStatus();
                    renderSavedList();
                })
                .show();
    }

    private void confirmResetCollectionIndex() {
        new AlertDialog.Builder(this)
                .setTitle("수집 이력 초기화")
                .setMessage("자동 수집에서 이미 처리한 메일로 기억한 이력을 지웁니다. 다음 일괄 수집 시 과거 메일이 다시 수집될 수 있습니다. 계속할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (d, w) -> {
                    int n = db.clearCollectionIndex();
                    toast("수집 이력 " + n + "건 초기화");
                })
                .show();
    }

    private void refreshGptBatchStatus() {
        if (gptBatchSummaryView == null || db == null) return;
        int analyzed = db.countGptAnalyzed();
        int pending = db.countGptUnanalyzed();
        String overview = prefs.getString(GptBatchHelper.PREF_GPT_OVERVIEW, "").trim();
        String line = "분석 완료 " + analyzed + " · 미분석 " + pending;
        if (!overview.isEmpty()) line += "\n최근 Inbox Brief · " + trim(overview, 220);
        else line += "\n추천: 신규/미분석 메일부터 GPT 전체분석";
        gptBatchSummaryView.setText(line);
    }

    private void chooseGptPackScope() {
        int pending = db.countGptUnanalyzed();
        int total = db.count();
        String[] labels = new String[]{
                "미분석 메일 " + pending + "건",
                "최근 50건",
                "최근 100건",
                "전체 저장 메일 (최대 200건)"
        };
        new AlertDialog.Builder(this)
                .setTitle("GPT 분석 범위")
                .setMessage("선택한 메일의 제목/발신자/수신자/최신 본문/로컬 태그를 JSON 파일로 만들어 ChatGPT 앱에 공유합니다.\n\n첨부파일은 포함하지 않습니다.")
                .setItems(labels, (d, which) -> {
                    boolean unanalyzedOnly = which == 0;
                    int limit = which == 1 ? 50 : (which == 2 ? 100 : 200);
                    String scope = which == 0 ? "미분석 메일" : (which == 1 ? "최근 50건" : (which == 2 ? "최근 100건" : "전체 저장 메일"));
                    List<SavedMailRecord> records = db.listForGpt(unanalyzedOnly, limit);
                    if (records.isEmpty()) {
                        toast(unanalyzedOnly ? "GPT 미분석 메일이 없습니다." : "공유할 저장 메일이 없습니다.");
                        return;
                    }
                    confirmShareGptPack(records, scope);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmShareGptPack(List<SavedMailRecord> records, String scope) {
        new AlertDialog.Builder(this)
                .setTitle("GPT 전체분석 공유")
                .setMessage(scope + " · " + records.size() + "건을 MAIL_PACK.json으로 만들어 ChatGPT 앱에 공유합니다.\n\n회사/조직 정책상 외부 AI 공유가 허용되는 메일인지 확인하세요. 계속할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("공유", (d, w) -> {
                    try {
                        java.io.File pack = GptBatchHelper.createMailPack(this, records, scope);
                        String prompt = GptBatchHelper.buildBatchPrompt(records.size(), scope);
                        GptBatchHelper.sharePack(this, pack, prompt);
                        toast("MAIL_PACK " + records.size() + "건을 ChatGPT로 공유합니다.");
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setTitle("GPT 분석팩 생성 실패").setMessage(e.getMessage()).setPositiveButton("확인", null).show();
                    }
                })
                .show();
    }

    private void showGptImportDialog() {
        String clip = GptBatchHelper.clipboardText(this);
        EditText input = new EditText(this);
        input.setMinLines(8);
        input.setMaxLines(18);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setText(clip);
        input.setHint("ChatGPT가 반환한 JSON 전체를 여기에 붙여넣으세요.");
        int pad = dp(18);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("GPT 결과 가져오기")
                .setMessage("ChatGPT 응답의 JSON 전체를 복사했다면 자동으로 채워집니다. 필요하면 직접 붙여넣으세요.")
                .setView(holder)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (d, w) -> applyGptImport(input.getText().toString()))
                .show();
    }

    private void applyGptImport(String raw) {
        if (raw == null || raw.trim().isEmpty()) { toast("가져올 JSON이 없습니다."); return; }
        try {
            GptBatchHelper.ImportResult result = GptBatchHelper.importJson(db, raw);
            if (!result.overview.trim().isEmpty()) {
                prefs.edit()
                        .putString(GptBatchHelper.PREF_GPT_OVERVIEW, result.overview)
                        .putLong(GptBatchHelper.PREF_GPT_OVERVIEW_TIME, System.currentTimeMillis())
                        .apply();
            }
            renderSavedList();
            refreshGptBatchStatus();
            toast("GPT 결과 " + result.applied + "건 적용");
            new AlertDialog.Builder(this)
                    .setTitle("GPT 분석 반영 완료")
                    .setMessage("적용 " + result.applied + "건 · ID 불일치/미적용 " + result.missing + "건\n\n" + (result.overview.trim().isEmpty() ? "Action Inbox에서 결과를 확인하세요." : trim(result.overview, 700)))
                    .setPositiveButton("Action Inbox 보기", (d, w) -> { currentFilter = "attention"; renderSavedList(); scrollToActionInbox(); })
                    .setNegativeButton("닫기", null)
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("GPT 결과 JSON 오류")
                    .setMessage("JSON을 읽지 못했습니다. ChatGPT 응답에서 JSON 전체를 복사했는지 확인하세요.\n\n" + e.getMessage())
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private void showRecommendedReply(SavedMailRecord r) {
        if (r == null || r.aiReplyDraft.trim().isEmpty()) { confirmGptReview(r); return; }
        String message = "[" + empty(r.aiPriority, "AI") + "] " + empty(r.aiSummary, "GPT 분석") +
                "\n\n다음 행동\n" + empty(r.aiNextAction, "—") +
                "\n\n추천 회신\n" + r.aiReplyDraft;
        new AlertDialog.Builder(this)
                .setTitle("GPT 추천 회신")
                .setMessage(message)
                .setNegativeButton("닫기", null)
                .setNeutralButton("GPT 재검토", (d, w) -> shareToChatGpt(r, true))
                .setPositiveButton("복사 + Secmail 열기", (d, w) -> {
                    GptBatchHelper.copyText(this, r.aiReplyDraft);
                    toast("추천 회신을 복사했습니다.");
                    openSecmail();
                })
                .show();
    }

    private void confirmGptReview(SavedMailRecord r) {
        // User already explicitly tapped GPT 검토 / 회신 준비.
        // Open ChatGPT immediately with the latest-message segment.
        toast("최신 메시지를 ChatGPT로 공유합니다.");
        shareToChatGpt(r, true);
    }

    private void confirmFullBodyShare(SavedMailRecord r) {
        new AlertDialog.Builder(this)
                .setTitle("최신 본문 포함")
                .setMessage("선택한 메일의 최신 메시지 구간을 ChatGPT 앱으로 공유합니다. 이전 회신 이력/서명은 가능한 한 제외합니다. 계속할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("보내기", (d, w) -> shareToChatGpt(r, true))
                .show();
    }

    private void shareToChatGpt(SavedMailRecord r, boolean includeBody) {
        String prompt = buildGptPrompt(r, includeBody);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, prompt);
        send.setPackage("com.openai.chatgpt");
        try {
            startActivity(send);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(Intent.EXTRA_TEXT, prompt);
            startActivity(Intent.createChooser(fallback, "GPT 검토에 사용할 앱 선택"));
        }
    }

    private String buildGptPrompt(SavedMailRecord r, boolean includeBody) {
        String latest = !r.latestBody.trim().isEmpty() ? r.latestBody : ImportantInfoExtractor.latestMessageBody(r.body);
        latest = sanitizeLatestForGpt(latest);
        if (latest.length() > 8000) latest = latest.substring(0, 8000) + "…";
        StringBuilder b = new StringBuilder();
        b.append("아래 업무 메일을 검토해줘.\n")
                .append("1) 상대방의 핵심 요청과 내가 확인해야 할 사항을 짧게 정리하고,\n")
                .append("2) 원문에 없는 사실은 만들지 말고 불명확한 내용은 [확인 필요]로 표시하고,\n")
                .append("3) 회사/제품/기술/시장 등 공개 외부정보 확인이 실제로 필요하면 웹 검색을 사용하고, 출처와 확인 시점을 함께 표시해줘.\n")
                .append("4) 웹 검색어에는 메일의 비공개 내용, 내부 가격/일정/캐파, 이메일 주소, 첨부파일 내용 등 기밀 가능 정보는 넣지 말고 공개 회사명/제품명/기술용어 수준만 사용해줘.\n")
                .append("5) 답변에서 [메일 원문 사실] / [웹 확인 사실] / [확인 필요]를 명확히 구분하고, 웹에서 확인되지 않은 내용을 사실처럼 보완하지 말아줘.\n")
                .append("6) 바로 보낼 수 있는 추천 회신 초안을 작성해줘. 상대방 메일 언어를 우선하고 마지막에 한글 요약도 붙여줘.\n\n")
                .append("[Subject] ").append(empty(ImportantInfoExtractor.cleanSubject(r.subject), "(없음)")).append("\n")
                .append("[From] ").append(empty(r.sender, "(미확인)")).append("\n")
                .append("[Date] ").append(empty(r.mailDate, "(미확인)")).append("\n")
                .append("[Local Workflow] ").append(workflowLabel(r.workflowStatus)).append("\n");
        if (!r.keyPoints.trim().isEmpty()) b.append("[Local 핵심 후보] ").append(trim(r.keyPoints, 1200)).append("\n");
        if (!r.actions.trim().isEmpty()) b.append("[Local Action 후보] ").append(trim(r.actions, 900)).append("\n");
        if (!r.schedules.trim().isEmpty()) b.append("[Local 일정 후보] ").append(trim(r.schedules, 500)).append("\n");
        if (includeBody) b.append("\n[Latest Message]\n").append(latest);
        else b.append("\n※ 원문 본문은 전달하지 않았고, 위 로컬 추출 정보만 제공했습니다. 정확한 회신 작성에 원문이 필요하면 필요한 부분을 요청해줘.");
        return b.toString();
    }

    private String sanitizeLatestForGpt(String source) {
        String x = source == null ? "" : source.replace("\r", "").trim();
        x = x.replaceAll("(?i)Caution: External Email\\.?\\s*", "")
                .replaceAll("(?i)Please take care when clicking links or opening attachments\\.?", "")
                .replaceAll("(?i)\\[EXTERNAL\\] This message comes from an external organization\\.[^\\n]*", "")
                .trim();
        String[] boundaries = new String[]{
                "\nThks,", "\nThanks,", "\nThank you,", "\nBest Regards", "\nBest Regard",
                "\n감사합니다.", "\nThe content of this e-mail is confidential", "\n此邮件可能包含机密信息"
        };
        int cut = x.length();
        for (String b : boundaries) {
            int i = x.toLowerCase(Locale.ROOT).indexOf(b.toLowerCase(Locale.ROOT));
            if (i > 40 && i < cut) cut = i;
        }
        return x.substring(0, cut).trim();
    }

    private void showSavedRecord(SavedMailRecord r) {
        currentMail = r.toSnapshot();
        currentSavedTranslation = r.translatedBody == null ? "" : r.translatedBody;
        currentInfo = ImportantInfoExtractor.extract(currentMail.subject, currentMail.body);
        bodyExpanded = true;
        renderMail();
        if (currentSavedTranslation.trim().isEmpty() && prefs.getBoolean(TranslationHelper.PREF_AUTO_TRANSLATE, true)) {
            String latest = !r.latestBody.trim().isEmpty() ? r.latestBody : ImportantInfoExtractor.latestMessageBody(r.body);
            TranslationHelper.translateAndStore(this, r.id, latest, (lang, translated, status) -> runOnUiThread(() -> {
                if (!translated.trim().isEmpty()) { currentSavedTranslation = translated; renderMail(); renderSavedList(); }
            }));
        }
        toast("상단 '최근 읽은 메일'에 저장 메일을 불러왔습니다.");
    }

    private String bullets(List<String> list) {
        if (list == null || list.isEmpty()) return "—";
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) b.append('\n');
            b.append("• ").append(s);
        }
        return b.toString();
    }

    private String firstNonEmptyLine(String s) {
        if (s == null) return "";
        for (String line : s.split("\\n")) if (!line.trim().isEmpty()) return line.trim();
        return "";
    }

    private String formatSavedAt(long ms) {
        if (ms <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= max ? x : x.substring(0, max - 1) + "…";
    }

    private String empty(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s.trim(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
