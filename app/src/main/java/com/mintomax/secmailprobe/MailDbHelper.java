package com.mintomax.secmailprobe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MailDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "secmail_assistant.db";
    private static final int DB_VERSION = 5;

    public static class SaveResult {
        public final long id;
        public final boolean inserted;
        public final boolean duplicate;
        public final boolean threadUpdate;
        SaveResult(long id, boolean inserted, boolean duplicate, boolean threadUpdate) {
            this.id = id;
            this.inserted = inserted;
            this.duplicate = duplicate;
            this.threadUpdate = threadUpdate;
        }
    }

    public MailDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE saved_mail (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subject TEXT, sender TEXT, recipients TEXT, mail_date TEXT, body TEXT, latest_body TEXT," +
                "detected_lang TEXT, translated_body TEXT, translation_status TEXT," +
                "captured_at TEXT, package_name TEXT, key_points TEXT, actions TEXT, schedules TEXT, topic_tags TEXT," +
                "workflow_status TEXT NOT NULL DEFAULT 'fyi', thread_key TEXT, source_list_signature TEXT," +
                "importance INTEGER NOT NULL DEFAULT 0, has_action INTEGER NOT NULL DEFAULT 0," +
                "has_schedule INTEGER NOT NULL DEFAULT 0, fingerprint TEXT UNIQUE," +
                "ai_class TEXT, ai_priority TEXT, ai_summary TEXT, ai_next_action TEXT, ai_reason TEXT," +
                "ai_reply_draft TEXT, ai_reply_language TEXT, ai_confidence REAL NOT NULL DEFAULT 0, ai_analyzed_at INTEGER NOT NULL DEFAULT 0," +
                "saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_saved_mail_saved_at ON saved_mail(saved_at DESC)");
        db.execSQL("CREATE INDEX idx_saved_mail_importance ON saved_mail(importance DESC)");
        db.execSQL("CREATE INDEX idx_saved_mail_workflow ON saved_mail(workflow_status)");
        db.execSQL("CREATE INDEX idx_saved_mail_thread ON saved_mail(thread_key)");
        db.execSQL("CREATE INDEX idx_saved_mail_ai_analyzed ON saved_mail(ai_analyzed_at)");
        createMailIndex(db);
    }

    private void createMailIndex(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mail_index (" +
                "list_signature TEXT PRIMARY KEY," +
                "fingerprint TEXT," +
                "preview TEXT," +
                "first_seen INTEGER NOT NULL," +
                "last_seen INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mail_index_last_seen ON mail_index(last_seen DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN importance INTEGER NOT NULL DEFAULT 0");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN has_action INTEGER NOT NULL DEFAULT 0");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN has_schedule INTEGER NOT NULL DEFAULT 0");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN fingerprint TEXT");
            safeExec(db, "CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_mail_fingerprint ON saved_mail(fingerprint)");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_saved_mail_saved_at ON saved_mail(saved_at DESC)");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_saved_mail_importance ON saved_mail(importance DESC)");
            backfillV2(db);
        }
        if (oldVersion < 3) {
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN latest_body TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN topic_tags TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN workflow_status TEXT NOT NULL DEFAULT 'fyi'");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN thread_key TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN source_list_signature TEXT");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_saved_mail_workflow ON saved_mail(workflow_status)");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_saved_mail_thread ON saved_mail(thread_key)");
            createMailIndex(db);
            backfillV3(db);
        }
        if (oldVersion < 4) {
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN detected_lang TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN translated_body TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN translation_status TEXT");
        }
        if (oldVersion < 5) {
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_class TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_priority TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_summary TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_next_action TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_reason TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_reply_draft TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_reply_language TEXT");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_confidence REAL NOT NULL DEFAULT 0");
            safeExec(db, "ALTER TABLE saved_mail ADD COLUMN ai_analyzed_at INTEGER NOT NULL DEFAULT 0");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_saved_mail_ai_analyzed ON saved_mail(ai_analyzed_at)");
        }
    }

    private void safeExec(SQLiteDatabase db, String sql) {
        try { db.execSQL(sql); } catch (Exception ignored) {}
    }

    private void backfillV2(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT id,subject,sender,recipients,mail_date,body,captured_at,package_name FROM saved_mail", null)) {
            while (c.moveToNext()) {
                MailSnapshot m = snapshotFromCursor(c);
                long id = c.getLong(0);
                ImportantInfoExtractor.Result info = ImportantInfoExtractor.extract(m.subject, m.body);
                ContentValues cv = new ContentValues();
                cv.put("fingerprint", fingerprintFor(m));
                cv.put("importance", info.importanceScore);
                cv.put("has_action", info.actions.isEmpty() ? 0 : 1);
                cv.put("has_schedule", info.schedules.isEmpty() ? 0 : 1);
                cv.put("key_points", join(info.keyPoints));
                cv.put("actions", join(info.actions));
                cv.put("schedules", join(info.schedules));
                db.update("saved_mail", cv, "id=?", new String[]{String.valueOf(id)});
            }
        } catch (Exception ignored) {}
    }

    private void backfillV3(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT id,subject,sender,recipients,mail_date,body,captured_at,package_name FROM saved_mail", null)) {
            while (c.moveToNext()) {
                MailSnapshot m = snapshotFromCursor(c);
                long id = c.getLong(0);
                ImportantInfoExtractor.Result info = ImportantInfoExtractor.extract(m.subject, m.body);
                ContentValues cv = new ContentValues();
                cv.put("latest_body", ImportantInfoExtractor.latestMessageBody(m.body));
                cv.put("topic_tags", join(info.topicTags));
                cv.put("workflow_status", info.workflowStatus);
                cv.put("thread_key", ImportantInfoExtractor.threadKey(m.subject));
                db.update("saved_mail", cv, "id=?", new String[]{String.valueOf(id)});
            }
        } catch (Exception ignored) {}
    }

    private MailSnapshot snapshotFromCursor(Cursor c) {
        MailSnapshot m = new MailSnapshot();
        m.subject = nz(c.getString(1));
        m.sender = nz(c.getString(2));
        m.recipients = nz(c.getString(3));
        m.mailDate = nz(c.getString(4));
        m.body = nz(c.getString(5));
        m.capturedAt = nz(c.getString(6));
        m.packageName = nz(c.getString(7));
        return m;
    }

    public SaveResult saveIfNew(MailSnapshot m, ImportantInfoExtractor.Result info) {
        return saveIfNew(m, info, "", "");
    }

    public SaveResult saveIfNew(MailSnapshot m, ImportantInfoExtractor.Result info, String listSignature, String listPreview) {
        if (m == null || !m.hasMailContent()) return new SaveResult(-1, false, false, false);
        if (info == null) info = ImportantInfoExtractor.extract(m.subject, m.body);
        String fp = fingerprintFor(m);
        String threadKey = ImportantInfoExtractor.threadKey(m.subject);
        boolean threadUpdate = countThread(threadKey) > 0;
        long existing = findIdByFingerprint(fp);
        if (existing > 0) {
            if (listSignature != null && !listSignature.isEmpty()) {
                ContentValues cv = new ContentValues();
                cv.put("source_list_signature", listSignature);
                getWritableDatabase().update("saved_mail", cv, "id=?", new String[]{String.valueOf(existing)});
                markCandidateProcessed(listSignature, fp, listPreview);
            }
            return new SaveResult(existing, false, true, threadUpdate);
        }

        ContentValues cv = new ContentValues();
        cv.put("subject", m.subject);
        cv.put("sender", m.sender);
        cv.put("recipients", m.recipients);
        cv.put("mail_date", m.mailDate);
        cv.put("body", m.body);
        cv.put("captured_at", m.capturedAt);
        cv.put("package_name", m.packageName);
        cv.putAll(baseAnalysisValues(m, info, listSignature));
        cv.put("fingerprint", fp);
        cv.put("saved_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertWithOnConflict("saved_mail", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (listSignature != null && !listSignature.isEmpty()) markCandidateProcessed(listSignature, fp, listPreview);
        if (id > 0) return new SaveResult(id, true, false, threadUpdate);
        existing = findIdByFingerprint(fp);
        return new SaveResult(existing, false, existing > 0, threadUpdate);
    }

    private ContentValues baseAnalysisValues(MailSnapshot m, ImportantInfoExtractor.Result info, String listSignature) {
        ContentValues cv = new ContentValues();
        cv.put("latest_body", ImportantInfoExtractor.latestMessageBody(m.body));
        cv.put("key_points", join(info.keyPoints));
        cv.put("actions", join(info.actions));
        cv.put("schedules", join(info.schedules));
        cv.put("topic_tags", join(info.topicTags));
        cv.put("workflow_status", info.workflowStatus);
        cv.put("thread_key", ImportantInfoExtractor.threadKey(m.subject));
        cv.put("source_list_signature", listSignature == null ? "" : listSignature);
        cv.put("importance", info.importanceScore);
        cv.put("has_action", info.actions.isEmpty() ? 0 : 1);
        cv.put("has_schedule", info.schedules.isEmpty() ? 0 : 1);
        return cv;
    }

    public int updateWorkflowStatus(long id, String status) {
        if (!isAllowedStatus(status)) return 0;
        ContentValues cv = new ContentValues();
        cv.put("workflow_status", status);
        return getWritableDatabase().update("saved_mail", cv, "id=?", new String[]{String.valueOf(id)});
    }

    private boolean isAllowedStatus(String s) {
        return "reply".equals(s) || "action".equals(s) || "waiting".equals(s) || "schedule".equals(s) || "fyi".equals(s) || "done".equals(s);
    }

    public int deleteById(long id) {
        return getWritableDatabase().delete("saved_mail", "id=?", new String[]{String.valueOf(id)});
    }

    /** Saved rows are deleted but collection index is intentionally retained so deleted mail is not auto-imported again. */
    public int deleteAll() {
        return getWritableDatabase().delete("saved_mail", null, null);
    }

    public int clearCollectionIndex() {
        return getWritableDatabase().delete("mail_index", null, null);
    }

    public int count() { return countFilter("all"); }

    public int countStatusSince(String status, long sinceMs) {
        if (!isAllowedStatus(status) || sinceMs <= 0L) return 0;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM saved_mail WHERE workflow_status=? AND saved_at>=?",
                new String[]{status, String.valueOf(sinceMs)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int countFilter(String filter) {
        String where = whereFor(filter);
        String sql = "SELECT COUNT(*) FROM saved_mail" + (where.isEmpty() ? "" : " WHERE " + where);
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<SavedMailRecord> list(String filter, int limit) {
        return list(filter, "", limit);
    }

    public List<SavedMailRecord> list(String filter, String query, int limit) {
        List<SavedMailRecord> out = new ArrayList<>();
        String where = whereFor(filter);
        List<String> args = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            String search = "(subject LIKE ? OR sender LIKE ? OR recipients LIKE ? OR latest_body LIKE ? OR translated_body LIKE ? OR key_points LIKE ? OR actions LIKE ? OR topic_tags LIKE ? OR ai_summary LIKE ? OR ai_next_action LIKE ? OR ai_reply_draft LIKE ?)";
            where = where.isEmpty() ? search : "(" + where + ") AND " + search;
            String like = "%" + q + "%";
            for (int i = 0; i < 11; i++) args.add(like);
        }
        String sql = "SELECT id,subject,sender,recipients,mail_date,body,latest_body,detected_lang,translated_body,translation_status,captured_at,package_name,key_points,actions,schedules,topic_tags," +
                "workflow_status,thread_key,source_list_signature,importance,saved_at,fingerprint,ai_class,ai_priority,ai_summary,ai_next_action,ai_reason,ai_reply_draft,ai_reply_language,ai_confidence,ai_analyzed_at FROM saved_mail" +
                (where.isEmpty() ? "" : " WHERE " + where) + " ORDER BY saved_at DESC LIMIT " + Math.max(1, Math.min(limit, 300));
        try (Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) out.add(recordFromCursor(c));
        }
        return out;
    }

    private SavedMailRecord recordFromCursor(Cursor c) {
        SavedMailRecord r = new SavedMailRecord();
        r.id = c.getLong(0);
        r.subject = nz(c.getString(1));
        r.sender = nz(c.getString(2));
        r.recipients = nz(c.getString(3));
        r.mailDate = nz(c.getString(4));
        r.body = nz(c.getString(5));
        r.latestBody = nz(c.getString(6));
        r.detectedLang = nz(c.getString(7));
        r.translatedBody = nz(c.getString(8));
        r.translationStatus = nz(c.getString(9));
        r.capturedAt = nz(c.getString(10));
        r.packageName = nz(c.getString(11));
        r.keyPoints = nz(c.getString(12));
        r.actions = nz(c.getString(13));
        r.schedules = nz(c.getString(14));
        r.topicTags = nz(c.getString(15));
        r.workflowStatus = nz(c.getString(16));
        r.threadKey = nz(c.getString(17));
        r.sourceListSignature = nz(c.getString(18));
        r.importance = c.getInt(19);
        r.savedAt = c.getLong(20);
        r.fingerprint = nz(c.getString(21));
        r.aiClass = nz(c.getString(22));
        r.aiPriority = nz(c.getString(23));
        r.aiSummary = nz(c.getString(24));
        r.aiNextAction = nz(c.getString(25));
        r.aiReason = nz(c.getString(26));
        r.aiReplyDraft = nz(c.getString(27));
        r.aiReplyLanguage = nz(c.getString(28));
        r.aiConfidence = c.getDouble(29);
        r.aiAnalyzedAt = c.getLong(30);
        return r;
    }


    public int countGptAnalyzed() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM saved_mail WHERE ai_analyzed_at>0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int countGptUnanalyzed() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM saved_mail WHERE ai_analyzed_at=0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<SavedMailRecord> listForGpt(boolean unanalyzedOnly, int limit) {
        List<SavedMailRecord> out = new ArrayList<>();
        String where = unanalyzedOnly ? " WHERE ai_analyzed_at=0" : "";
        String sql = "SELECT id,subject,sender,recipients,mail_date,body,latest_body,detected_lang,translated_body,translation_status,captured_at,package_name,key_points,actions,schedules,topic_tags," +
                "workflow_status,thread_key,source_list_signature,importance,saved_at,fingerprint,ai_class,ai_priority,ai_summary,ai_next_action,ai_reason,ai_reply_draft,ai_reply_language,ai_confidence,ai_analyzed_at FROM saved_mail" +
                where + " ORDER BY saved_at DESC LIMIT " + Math.max(1, Math.min(limit, 200));
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(recordFromCursor(c));
        }
        return out;
    }

    public int updateGptAnalysis(String fingerprint, String aiClass, String priority, String summary, String nextAction,
                                 String reason, String replyDraft, String replyLanguage, double confidence) {
        if (fingerprint == null || fingerprint.trim().isEmpty()) return 0;
        ContentValues cv = new ContentValues();
        cv.put("ai_class", nz(aiClass));
        cv.put("ai_priority", nz(priority));
        cv.put("ai_summary", nz(summary));
        cv.put("ai_next_action", nz(nextAction));
        cv.put("ai_reason", nz(reason));
        cv.put("ai_reply_draft", nz(replyDraft));
        cv.put("ai_reply_language", nz(replyLanguage));
        cv.put("ai_confidence", confidence);
        cv.put("ai_analyzed_at", System.currentTimeMillis());
        String cls = aiClass == null ? "" : aiClass.trim().toLowerCase(Locale.ROOT);
        if ("reply".equals(cls) || "action".equals(cls) || "schedule".equals(cls) || "fyi".equals(cls)) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT workflow_status FROM saved_mail WHERE fingerprint=? LIMIT 1", new String[]{fingerprint})) {
                if (c.moveToFirst()) {
                    String current = nz(c.getString(0));
                    if (!"waiting".equals(current) && !"done".equals(current)) cv.put("workflow_status", cls);
                }
            }
        }
        return getWritableDatabase().update("saved_mail", cv, "fingerprint=?", new String[]{fingerprint});
    }

    public int updateTranslation(long id, String language, String translated, String status) {
        if (id <= 0) return 0;
        ContentValues cv = new ContentValues();
        cv.put("detected_lang", language == null ? "" : language);
        cv.put("translated_body", translated == null ? "" : translated);
        cv.put("translation_status", status == null ? "" : status);
        return getWritableDatabase().update("saved_mail", cv, "id=?", new String[]{String.valueOf(id)});
    }

    private String whereFor(String filter) {
        if (filter == null) return "";
        switch (filter.toLowerCase(Locale.ROOT)) {
            case "attention": return "workflow_status IN ('reply','action')";
            case "waiting": return "workflow_status='waiting'";
            case "schedule": return "workflow_status='schedule'";
            case "fyi": return "workflow_status='fyi'";
            case "done": return "workflow_status='done'";
            case "important": return "importance >= 3";
            case "action": return "has_action = 1";
            default: return "";
        }
    }

    public boolean isCandidateProcessed(String signature) {
        if (signature == null || signature.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM mail_index WHERE list_signature=? LIMIT 1", new String[]{signature})) {
            return c.moveToFirst();
        }
    }

    public void touchCandidate(String signature) {
        if (signature == null || signature.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("last_seen", System.currentTimeMillis());
        getWritableDatabase().update("mail_index", cv, "list_signature=?", new String[]{signature});
    }

    public void markCandidateProcessed(String signature, String fingerprint, String preview) {
        if (signature == null || signature.isEmpty()) return;
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("list_signature", signature);
        cv.put("fingerprint", fingerprint == null ? "" : fingerprint);
        cv.put("preview", preview == null ? "" : preview);
        cv.put("first_seen", now);
        cv.put("last_seen", now);
        getWritableDatabase().insertWithOnConflict("mail_index", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues update = new ContentValues();
        update.put("fingerprint", fingerprint == null ? "" : fingerprint);
        update.put("preview", preview == null ? "" : preview);
        update.put("last_seen", now);
        getWritableDatabase().update("mail_index", update, "list_signature=?", new String[]{signature});
    }

    private long findIdByFingerprint(String fp) {
        if (fp == null || fp.isEmpty()) return -1;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM saved_mail WHERE fingerprint=? LIMIT 1", new String[]{fp})) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    private int countThread(String key) {
        if (key == null || key.isEmpty()) return 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM saved_mail WHERE thread_key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public static String fingerprintFor(MailSnapshot m) {
        String basis = norm(m.sender) + "|" + norm(ImportantInfoExtractor.cleanSubject(m.subject)) + "|" +
                norm(m.mailDate) + "|" + norm(ImportantInfoExtractor.latestMessageBody(m.body));
        return sha256(basis);
    }

    public static String listSignatureFor(String compact) {
        return sha256(norm(compact));
    }

    private static String sha256(String basis) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] bytes = d.digest((basis == null ? "" : basis).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : bytes) b.append(String.format(Locale.US, "%02x", x & 0xff));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString((basis == null ? "" : basis).hashCode());
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        if (list == null) return "";
        for (String s : list) {
            if (b.length() > 0) b.append("\n");
            b.append(s);
        }
        return b.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
