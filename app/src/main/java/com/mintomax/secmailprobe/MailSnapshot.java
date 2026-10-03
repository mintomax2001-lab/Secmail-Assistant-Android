package com.mintomax.secmailprobe;

public class MailSnapshot {
    public String subject = "";
    public String sender = "";
    public String recipients = "";
    public String mailDate = "";
    public String body = "";
    public String capturedAt = "";
    public String packageName = "";

    public boolean hasMailContent() {
        return !subject.trim().isEmpty() || !body.trim().isEmpty();
    }

    public boolean looksComplete() {
        return !subject.trim().isEmpty() && body != null && body.trim().length() >= 3;
    }
}
