package com.example.pdfapplication.controller;

import com.example.pdfapplication.dto.ImageResponse;
import com.example.pdfapplication.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// For zipping multiple files as response
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.io.ByteArrayOutputStream; // Should be there
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfService pdfService;

    @Autowired
    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping("/render")
    public ResponseEntity<List<ImageResponse>> renderPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // Check for content type
        if (file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            // Consider creating a specific error DTO later
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        try {
            List<ImageResponse> images = pdfService.renderPdfToImages(file);
            if (images.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(images);
        } catch (IOException e) {
            // Log error e.printStackTrace();
            // Consider creating a specific error DTO later
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<byte[]> mergePdfs(@RequestParam("files") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid or non-PDF file detected".getBytes());
            }
        }

        try {
            byte[] mergedPdfBytes = pdfService.mergePdfs(files);
            if (mergedPdfBytes.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to merge PDFs".getBytes());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "merged.pdf"); // Suggests download
            headers.setContentLength(mergedPdfBytes.length);

            return new ResponseEntity<>(mergedPdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error during PDF merge: " + e.getMessage()).getBytes());
        }
    }

    @PostMapping("/add-watermark")
    public ResponseEntity<byte[]> addWatermark(@RequestParam("file") MultipartFile file, @RequestParam("text") String text) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid or non-PDF file detected".getBytes());
        }
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Watermark text cannot be empty".getBytes());
        }

        try {
            byte[] watermarkedPdfBytes = pdfService.addWatermark(file, text);
            if (watermarkedPdfBytes.length == 0) {
                // This case might not be hit if document.save always produces some bytes for a valid doc
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to add watermark".getBytes());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "watermarked_document.pdf");
            headers.setContentLength(watermarkedPdfBytes.length);

            return new ResponseEntity<>(watermarkedPdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error during watermark addition: " + e.getMessage()).getBytes());
        }
    }

    @PostMapping("/split")
    public ResponseEntity<byte[]> splitPdf(@RequestParam("file") MultipartFile file, @RequestParam("ranges") String ranges) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid or non-PDF file detected".getBytes());
        }
        if (ranges == null || ranges.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Page ranges not specified".getBytes());
        }

        try {
            List<byte[]> splitPdfBytesList = pdfService.splitPdf(file, ranges);

            if (splitPdfBytesList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No pages found for specified ranges or PDF is empty.".getBytes());
            }

            // If only one PDF is created, return it directly
            if (splitPdfBytesList.size() == 1) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "split_doc.pdf");
                headers.setContentLength(splitPdfBytesList.get(0).length);
                return new ResponseEntity<>(splitPdfBytesList.get(0), headers, HttpStatus.OK);
            }

            // If multiple PDFs, zip them
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (int i = 0; i < splitPdfBytesList.size(); i++) {
                    ZipEntry entry = new ZipEntry("split_doc_" + (i + 1) + ".pdf");
                    zos.putNextEntry(entry);
                    zos.write(splitPdfBytesList.get(i));
                    zos.closeEntry();
                }
            } // ZipOutputStream is closed here

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // For ZIP
            headers.setContentDispositionFormData("attachment", "split_documents.zip");
            headers.setContentLength(baos.size());

            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);

        } catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error during PDF split: " + e.getMessage()).getBytes());
        } catch (IllegalArgumentException e) {
             // Log error: e.printStackTrace();
            return ResponseEntity.badRequest().body(("Invalid page ranges: " + e.getMessage()).getBytes());
        }
    }

    @PostMapping("/remove-watermark")
    public ResponseEntity<byte[]> removeWatermark(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid or non-PDF file detected".getBytes());
        }

        try {
            // Log or acknowledge the limitations of this feature.
            System.out.println("Received request for /remove-watermark. Feature has limitations.");
            byte[] processedPdfBytes = pdfService.removeWatermark(file);

            // Since the basic implementation just returns the original bytes,
            // the response will be the original PDF.
            // A real implementation might return a modified PDF or an error if no watermark found/removed.

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            // Filename could be "original_document.pdf" or "processed_document.pdf"
            headers.setContentDispositionFormData("attachment", "processed_document.pdf");
            headers.setContentLength(processedPdfBytes.length);

            return new ResponseEntity<>(processedPdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            // Log error: e.printStackTrace();
            System.err.println("Error during (placeholder) watermark removal: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error during watermark processing: " + e.getMessage()).getBytes());
        }
    }

    @PostMapping("/swap-pages")
    public ResponseEntity<?> swapPages(@RequestParam("file") MultipartFile file,
                                       @RequestParam("pageNum1") int pageNum1,
                                       @RequestParam("pageNum2") int pageNum2) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid or non-PDF file detected.");
        }
        if (pageNum1 <= 0 || pageNum2 <= 0) {
            return ResponseEntity.badRequest().body("Page numbers must be positive.");
        }
        if (pageNum1 == pageNum2) {
            return ResponseEntity.badRequest().body("Page numbers to swap must be different.");
        }

        try {
            byte[] modifiedPdfBytes = pdfService.swapPages(file, pageNum1, pageNum2);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "pages_swapped.pdf");
            headers.setContentLength(modifiedPdfBytes.length);

            return new ResponseEntity<>(modifiedPdfBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during page swapping: " + e.getMessage());
        }
    }

    @PostMapping("/insert-page")
    public ResponseEntity<?> insertPage(@RequestParam("mainFile") MultipartFile mainFile,
                                        @RequestParam("pagesToInsertFile") MultipartFile pagesToInsertFile,
                                        @RequestParam("insertAtPageNum") int insertAtPageNum) {

        if (mainFile.isEmpty() || mainFile.getContentType() == null || !mainFile.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Main file is invalid or not a PDF.");
        }
        if (pagesToInsertFile.isEmpty() || pagesToInsertFile.getContentType() == null || !pagesToInsertFile.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("File with pages to insert is invalid or not a PDF.");
        }
        if (insertAtPageNum <= 0) {
            return ResponseEntity.badRequest().body("Insertion page number must be positive.");
        }

        try {
            byte[] modifiedPdfBytes = pdfService.insertPages(mainFile, pagesToInsertFile, insertAtPageNum);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "document_with_inserted_pages.pdf");
            headers.setContentLength(modifiedPdfBytes.length);

            return new ResponseEntity<>(modifiedPdfBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during page insertion: " + e.getMessage());
        }
    }

    @PostMapping("/delete-page")
    public ResponseEntity<?> deletePage(@RequestParam("file") MultipartFile file,
                                        @RequestParam("pagesToDelete") String pagesToDelete) {

        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("File is invalid or not a PDF.");
        }
        if (pagesToDelete == null || pagesToDelete.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Page numbers/ranges to delete cannot be empty.");
        }

        try {
            byte[] modifiedPdfBytes = pdfService.deletePages(file, pagesToDelete);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "document_with_deleted_pages.pdf");
            headers.setContentLength(modifiedPdfBytes.length);

            return new ResponseEntity<>(modifiedPdfBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            // Log error: e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during page deletion: " + e.getMessage());
        }
    }
}
