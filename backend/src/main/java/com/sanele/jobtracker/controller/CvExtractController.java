package com.sanele.jobtracker.controller;

import com.sanele.jobtracker.service.CvTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Turns an uploaded CV (PDF / DOCX) into plain text so the register + profile
 * forms can store and match it. Public — it runs during sign-up, before the
 * account exists, and stores nothing.
 */
@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
public class CvExtractController {

    private final CvTextExtractor extractor;

    @PostMapping("/extract")
    public ResponseEntity<Map<String, String>> extract(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "No file was uploaded."));
        }
        try {
            String text = extractor.extract(file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return ResponseEntity.ok(Map.of("text", text));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Could not read the uploaded file."));
        }
    }
}
