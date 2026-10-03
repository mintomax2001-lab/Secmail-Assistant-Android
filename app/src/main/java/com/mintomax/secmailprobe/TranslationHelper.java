package com.mintomax.secmailprobe;

import android.content.Context;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/**
 * Optional on-device machine translation helper.
 * Input mail text is processed on-device by ML Kit. Network is used only when
 * language models/SDK updates are needed. Translation is UI assistance only;
 * classification/fingerprints remain based on the original mail text.
 */
public final class TranslationHelper {
    public static final String PREF_AUTO_TRANSLATE = "auto_translate";
    private static final int MAX_TRANSLATE_CHARS = 7000;

    public interface Callback {
        void onComplete(String language, String translated, String status);
    }

    public interface PrepareCallback {
        void onProgress(String status);
        void onComplete(boolean ok, String status);
    }

    private TranslationHelper() {}

    public static void translateAndStore(Context context, long mailId, String text, Callback callback) {
        translateToKorean(context, text, (lang, translated, status) -> {
            if (mailId > 0) {
                new MailDbHelper(context.getApplicationContext())
                        .updateTranslation(mailId, lang, translated, status);
            }
            if (callback != null) callback.onComplete(lang, translated, status);
        });
    }

    public static void translateToKorean(Context context, String text, Callback callback) {
        String src = text == null ? "" : text.trim();
        if (src.isEmpty()) {
            finish(callback, "", "", "empty");
            return;
        }
        if (src.length() > MAX_TRANSLATE_CHARS) src = src.substring(0, MAX_TRANSLATE_CHARS);
        final String input = src;

        LanguageIdentifier identifier = LanguageIdentification.getClient();
        identifier.identifyLanguage(input)
                .addOnSuccessListener(code -> {
                    identifier.close();
                    if (code == null || "und".equalsIgnoreCase(code)) {
                        finish(callback, "und", "", "language_unknown");
                        return;
                    }
                    if (code.toLowerCase().startsWith("ko")) {
                        finish(callback, code, "", "already_korean");
                        return;
                    }
                    String source = TranslateLanguage.fromLanguageTag(code);
                    if (source == null) {
                        finish(callback, code, "", "unsupported_language");
                        return;
                    }
                    TranslatorOptions options = new TranslatorOptions.Builder()
                            .setSourceLanguage(source)
                            .setTargetLanguage(TranslateLanguage.KOREAN)
                            .build();
                    Translator translator = Translation.getClient(options);
                    DownloadConditions conditions = new DownloadConditions.Builder().build();
                    translator.downloadModelIfNeeded(conditions)
                            .addOnSuccessListener(v -> translator.translate(input)
                                    .addOnSuccessListener(result -> {
                                        translator.close();
                                        finish(callback, code, result == null ? "" : result.trim(), "translated");
                                    })
                                    .addOnFailureListener(err -> {
                                        translator.close();
                                        finish(callback, code, "", "translate_failed");
                                    }))
                            .addOnFailureListener(err -> {
                                translator.close();
                                finish(callback, code, "", "model_download_failed");
                            });
                })
                .addOnFailureListener(err -> {
                    identifier.close();
                    finish(callback, "", "", "language_id_failed");
                });
    }

    /** Pre-downloads the two most useful source models for this project. */
    public static void prepareEnglishChineseModels(Context context, PrepareCallback callback) {
        if (callback != null) callback.onProgress("영어→한국어 모델 준비 중…");
        preparePair(TranslateLanguage.ENGLISH, okEn -> {
            if (!okEn) {
                if (callback != null) callback.onComplete(false, "영어 모델 다운로드 실패");
                return;
            }
            if (callback != null) callback.onProgress("중국어→한국어 모델 준비 중…");
            preparePair(TranslateLanguage.CHINESE, okZh -> {
                if (callback != null) callback.onComplete(okZh,
                        okZh ? "영어/중국어 번역 모델 준비 완료" : "중국어 모델 다운로드 실패");
            });
        });
    }

    private interface PairCallback { void done(boolean ok); }

    private static void preparePair(String sourceLanguage, PairCallback callback) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build();
        Translator translator = Translation.getClient(options);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    translator.close();
                    callback.done(true);
                })
                .addOnFailureListener(err -> {
                    translator.close();
                    callback.done(false);
                });
    }

    private static void finish(Callback callback, String language, String translated, String status) {
        if (callback != null) callback.onComplete(language == null ? "" : language,
                translated == null ? "" : translated,
                status == null ? "" : status);
    }
}
