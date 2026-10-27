package com.sanele.jobtracker.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pulls plain text out of an uploaded CV so it can be stored + matched. Supports
 * PDF (PDFBox), DOCX (word/document.xml out of the zip), and plain text. Anything
 * else is rejected with a message telling the user to paste instead.
 */
@Component
public class CvTextExtractor {

    private static final int MAX = 20_000;

    /** @throws IllegalArgumentException for unsupported/empty files (message is user-facing). */
    public String extract(String filename, String contentType, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase();
        String type = contentType == null ? "" : contentType.toLowerCase();

        String text;
        if (name.endsWith(".pdf") || type.contains("pdf")) {
            text = fromPdf(bytes);
        } else if (name.endsWith(".docx") || type.contains("wordprocessingml")) {
            text = fromDocx(bytes);
        } else if (name.endsWith(".txt") || name.endsWith(".md") || type.startsWith("text/")) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type — upload a PDF, DOCX or TXT, or paste your CV.");
        }

        text = clean(text);
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "Couldn't read any text from that file — try another file, or paste your CV.");
        }
        return text.length() > MAX ? text.substring(0, MAX) : text;
    }

    private String fromPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new IllegalArgumentException("That PDF couldn't be read — try another file, or paste your CV.");
        }
    }

    private String fromDocx(byte[] bytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    // preserve paragraph / tab breaks, then strip the XML tags
                    xml = xml.replaceAll("</w:p>", "\n").replace("<w:tab/>", "\t");
                    String stripped = xml.replaceAll("(?s)<[^>]+>", "");
                    return stripped
                            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                            .replace("&quot;", "\"").replace("&apos;", "'");
                }
            }
            return "";
        } catch (IOException e) {
            throw new IllegalArgumentException("That document couldn't be read — try another file, or paste your CV.");
        }
    }

    /** Collapse runaway whitespace but keep line structure. */
    private static String clean(String text) {
        if (text == null) return "";
        return text
                .replace("\r", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
