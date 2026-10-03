package com.mintomax.secmailprobe;

public class SavedMailRecord {
    public long id;
    public String subject = "";
    public String sender = "";
    public String recipients = "";
    public String mailDate = "";
    public String body = "";
    public String latestBody = "";
    public String detectedLang = "";
    public String translatedBody = "";
    public String translationStatus = "";
    public String capturedAt = "";
    public String packageName = "";
    public String keyPoints = "";
    public String actions = "";
    public String schedules = "";
    public String topicTags = "";
    public String workflowStatus = "fyi";
    public String threadKey = "";
    public String sourceListSignature = "";
    public int importance;
    public long savedAt;
    public String fingerprint = "";
    public String aiClass = "";
    public String aiPriority = "";
    public String aiSummary = "";
    public String aiNextAction = "";
    public String aiReason = "";
    public String aiReplyDraft = "";
    public String aiReplyLanguage = "";
    public double aiConfidence;
    public long aiAnalyzedAt;

    public MailSnapshot toSnapshot() {
        MailSnapshot m = new MailSnapshot();
        m.subject = subject;
        m.sender = sender;
        m.recipients = recipients;
        m.mailDate = mailDate;
        m.body = body;
        m.capturedAt = capturedAt;
        m.packageName = packageName;
        return m;
    }
}
