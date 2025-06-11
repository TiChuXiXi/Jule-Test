package com.example.pdfapplication.service;

import com.example.pdfapplication.dto.ImageResponse;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter; // For splitting
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream; // For packaging multiple files
import java.util.zip.ZipEntry; // For packaging multiple files
import org.apache.pdfbox.util.Matrix;
import java.awt.Color; // If not already imported
import java.util.Collections; // For Collections.swap


@Service
public class PdfService {

    public List<ImageResponse> renderPdfToImages(MultipartFile file) throws IOException {
        List<ImageResponse> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                images.add(new ImageResponse("page_" + (page + 1) + ".png", "png", base64Image));
            }
        }
        return images;
    }

    public byte[] mergePdfs(List<MultipartFile> files) throws IOException {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (MultipartFile file : files) {
            try (PDDocument document = PDDocument.load(file.getInputStream())) {
                // Setting destination stream is not needed per file,
                // but adding sources is.
                // For PDFMergerUtility, we add sources and then merge.
                pdfMerger.addSource(file.getInputStream());
            }
        }
        // The output stream for the merged PDF.
        pdfMerger.setDestinationStream(baos);
        // Merge the documents.
        pdfMerger.mergeDocuments(null); // Memory usage can be optimized with MemoryUsageSetting

        return baos.toByteArray();
    }

    // Helper method to parse page ranges like "1-3,5,7-"
    private List<Integer> parsePageRanges(String pageRangesStr, int totalPages) {
        List<Integer> pagesToExtract = new ArrayList<>();
        if (pageRangesStr == null || pageRangesStr.trim().isEmpty()) {
            // If no range is specified, consider extracting all pages individually by some convention
            // Or, this could be an error. For now, let's assume it means "don't split" or invalid.
            // Depending on exact requirements, might add all pages: for(int i=0; i<totalPages; i++) pagesToExtract.add(i);
            return pagesToExtract; // Or throw IllegalArgumentException
        }

        String[] ranges = pageRangesStr.split(",");
        for (String range : ranges) {
            range = range.trim();
            if (range.contains("-")) {
                String[] parts = range.split("-");
                int start = Integer.parseInt(parts[0]) - 1; // 0-indexed
                int end;
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Integer.parseInt(parts[1]) - 1;
                } else { // e.g., "7-" means 7 to end
                    end = totalPages - 1;
                }
                // Add basic validation
                if (start < 0) start = 0;
                if (end >= totalPages) end = totalPages -1;
                if (start > end) continue; // or throw error

                for (int i = start; i <= end; i++) {
                    if (!pagesToExtract.contains(i)) {
                        pagesToExtract.add(i);
                    }
                }
            } else {
                int pageNum = Integer.parseInt(range) - 1; // 0-indexed
                 if (pageNum >= 0 && pageNum < totalPages && !pagesToExtract.contains(pageNum)){
                    pagesToExtract.add(pageNum);
                 }
            }
        }
        java.util.Collections.sort(pagesToExtract); // Ensure pages are in order
        return pagesToExtract;
    }

    public List<byte[]> splitPdf(MultipartFile file, String pageRangesStr) throws IOException {
        List<byte[]> splitDocumentsBytes = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            List<Integer> pagesToSplitAt = parsePageRangesForSplitter(pageRangesStr, document.getNumberOfPages());

            if (pagesToSplitAt.isEmpty()) {
                 // If parsePageRangesForSplitter returns empty, it implies either an error in range
                 // or a request to not split, or split into single pages.
                 // For now, let's assume it means each page as a separate document if range is e.g. "*" or empty.
                 // This specific implementation of parsePageRangesForSplitter needs to be robust.
                 // The current Splitter class is better used by telling it where to split.
                 // E.g., split.setSplitAtPage(int page)
                 // So, the logic for "1-3,5" needs to map to multiple split operations or manual page copying.

                 // Alternative: Manual page copying for arbitrary ranges
                 return extractPagesManually(document, pageRangesStr);
            }

            // Using PDFBox Splitter if the pageRangesStr can be mapped to its usage.
            // The Splitter class is designed to split a PDF into multiple parts,
            // e.g., every N pages, or at specific page numbers.
            // If pageRangesStr is "1-3,5,7-", it implies creating a PDF with pages 1-3, another with 5, another with 7-end.
            // This is not a direct "split at" operation.
            // So, manual extraction is more suitable here.
        }
        // Fallback or direct call to manual extraction based on refined logic
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
             return extractPagesManually(document, pageRangesStr);
        }
    }

    // This helper is more aligned with how Splitter class is typically used (split points)
    // For "1-3,5,7-", this isn't the direct list of pages, but where to make a cut.
    // This might need rethinking if using Splitter directly.
    private List<Integer> parsePageRangesForSplitter(String pageRangesStr, int totalPages) {
        List<Integer> splitPoints = new ArrayList<>();
        // Example: if pageRangesStr is "3,5", it means split after page 3, and after page 5.
        // This is different from "extract pages 3 and 5".
        // For now, let's assume `pageRangesStr` defines which pages to *keep* in separate documents.
        // This means the `splitPdf` method should really be `extractPdfPagesIntoSeparateDocuments`.
        // The current plan step "Implement PDF Split API" might imply both "split every X pages" and "extract specific pages".
        // Let's stick to "extract specific pages/ranges into separate documents".
        return splitPoints; // Placeholder, as manual extraction below is better for this.
    }

    // Method to extract specified pages/ranges into separate PDF documents
    private List<byte[]> extractPagesManually(PDDocument document, String pageRangesStr) throws IOException {
        List<byte[]> resultByteArrays = new ArrayList<>();
        List<List<Integer>> pageGroups = parsePageGroupsForExtraction(pageRangesStr, document.getNumberOfPages());

        for (List<Integer> group : pageGroups) {
            if (group.isEmpty()) continue;
            try (PDDocument newDoc = new PDDocument()) {
                for (Integer pageIndex : group) { // pageIndex should be 0-based
                    if (pageIndex >= 0 && pageIndex < document.getNumberOfPages()) {
                        newDoc.addPage(document.getPage(pageIndex));
                    }
                }
                if (newDoc.getNumberOfPages() > 0) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    newDoc.save(baos);
                    resultByteArrays.add(baos.toByteArray());
                }
            }
        }
        return resultByteArrays;
    }

    // Parses "1-3,5,7-" into groups of pages: [[0,1,2], [4], [6,7,...,N-1]]
    private List<List<Integer>> parsePageGroupsForExtraction(String pageRangesStr, int totalPages) {
        List<List<Integer>> pageGroups = new ArrayList<>();
        if (pageRangesStr == null || pageRangesStr.trim().isEmpty()) {
            // Default: extract all pages individually
            for(int i=0; i<totalPages; ++i) {
                List<Integer> singlePageGroup = new ArrayList<>();
                singlePageGroup.add(i);
                pageGroups.add(singlePageGroup);
            }
            return pageGroups;
        }

        String[] ranges = pageRangesStr.split(",");
        for (String range : ranges) {
            range = range.trim();
            List<Integer> currentGroup = new ArrayList<>();
            if (range.contains("-")) {
                String[] parts = range.split("-");
                int start = Integer.parseInt(parts[0]) - 1; // 0-indexed
                int end;
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Integer.parseInt(parts[1]) - 1;
                } else { // e.g., "7-" means 7 to end
                    end = totalPages - 1;
                }
                // Basic validation
                start = Math.max(0, start);
                end = Math.min(totalPages - 1, end);

                if (start <= end) {
                    for (int i = start; i <= end; i++) {
                        currentGroup.add(i);
                    }
                }
            } else {
                int pageNum = Integer.parseInt(range) - 1; // 0-indexed
                if (pageNum >= 0 && pageNum < totalPages){
                    currentGroup.add(pageNum);
                }
            }
            if (!currentGroup.isEmpty()) {
                pageGroups.add(currentGroup);
            }
        }
        return pageGroups;
    }

    public byte[] removeWatermark(MultipartFile file) throws IOException {
        System.out.println("Attempting to remove watermark. NOTE: This is a basic implementation with significant limitations.");
        System.out.println("Generic watermark removal is complex. This method currently returns the original PDF bytes.");

        // Placeholder for actual watermark removal logic.
        // A true implementation would require identifying watermark objects (text, images)
        // based on some criteria (e.g., specific text, position, graphics state, metadata)
        // and then attempting to remove or obscure them. This is non-trivial.
        //
        // Example of what one *might* try (highly specific and likely to fail for general cases):
        // try (PDDocument document = PDDocument.load(file.getInputStream())) {
        //     for (PDPage page : document.getPages()) {
        //         // Iterate through content streams, identify watermark elements, remove them.
        //         // This requires a deep understanding of PDF content stream operators.
        //         // For instance, if watermarks were added as XObjects with a specific name:
        //         // PDResources resources = page.getResources();
        //         // if (resources != null) {
        //         //     for (COSName name : resources.getXObjectNames()) {
        //         //         if ("MyKnownWatermarkXObjectName".equals(name.getName())) {
        //         //             resources.getCOSObject().removeItem(name); // Simplified example
        //         //         }
        //         //     }
        //         // }
        //     }
        //     ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //     document.save(baos);
        //     return baos.toByteArray();
        // }

        // For now, just return the original file bytes
        return file.getBytes();
    }

    public byte[] addWatermark(MultipartFile file, String watermarkText) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            for (PDPage page : document.getPages()) {
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // Set font and color
                    // Using standard font, consider allowing font customization later
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 48);
                    contentStream.setNonStrokingColor(Color.LIGHT_GRAY); // Watermark color

                    // Set transparency
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(0.2f); // 20% opacity
                    contentStream.setGraphicsStateParameters(graphicsState);

                    // Get page dimensions
                    float pageWidth = page.getMediaBox().getWidth();
                    float pageHeight = page.getMediaBox().getHeight();

                    // Calculate text width (approximation)
                    // For accurate width, would need to use font.getStringWidth(text) / 1000 * fontSize
                    // This is a simplified centering
                    float textWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(watermarkText) / 1000 * 48;
                    float textHeight = 48; // Approximation based on font size

                    // Position the text (e.g., centered and rotated)
                    contentStream.beginText();

                    // Center of page
                    float centerX = pageWidth / 2;
                    float centerY = pageHeight / 2;

                    // Create a rotation matrix for diagonal text
                    Matrix matrix = Matrix.getRotateInstance(Math.toRadians(45), centerX, centerY);
                    contentStream.setTextMatrix(matrix);

                    // Adjust position to truly center after rotation (this is tricky)
                    // For simpler centered (non-rotated) text:
                    // contentStream.newLineAtOffset((pageWidth - textWidth) / 2, (pageHeight - textHeight) / 2);
                    // For rotated text, the effective bounding box changes.
                    // A common approach is to place it at center and let rotation handle it.
                    // For more precise placement of rotated text's visual center:
                    // Calculate the new position based on the text length and angle.
                    // For now, let's use a simpler approach:
                    contentStream.newLineAtOffset(centerX - textWidth / 2, centerY); // This centers before rotation around bottom-left of text

                    contentStream.showText(watermarkText);
                    contentStream.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    public byte[] swapPages(MultipartFile file, int pageNum1, int pageNum2) throws IOException {
        // Convert to 0-indexed for PDFBox internal use
        int p1 = pageNum1 - 1;
        int p2 = pageNum2 - 1;

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            int numberOfPages = document.getNumberOfPages();

            // Validate page numbers
            if (p1 < 0 || p1 >= numberOfPages || p2 < 0 || p2 >= numberOfPages) {
                throw new IllegalArgumentException("Page numbers are out of bounds. PDF has " + numberOfPages + " pages.");
            }
            if (p1 == p2) {
                throw new IllegalArgumentException("Page numbers to swap must be different.");
            }

            // Simpler approach: Get all pages, swap in a list, then re-create the document pages.
            // This avoids issues with index changes during removal if using PDDocument.removePage directly multiple times.

            // 1. Extract all pages into a temporary list
            List<PDPage> allPages = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                allPages.add(page);
            }

            // 2. Swap the pages in the list
            Collections.swap(allPages, p1, p2);

            // 3. Remove all pages from the document
            // Iterate backwards to avoid issues with shrinking size and index changes
            for (int i = numberOfPages - 1; i >= 0; i--) {
                document.removePage(i);
            }

            // 4. Add pages back from the modified list
            for (PDPage page : allPages) {
                document.addPage(page);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    public byte[] insertPages(MultipartFile mainFile, MultipartFile pagesToInsertFile, int insertAtPageNumApi) throws IOException {
        // API uses 1-indexed page numbers. insertAtPageNumApi = 1 means insert at the very beginning.
        // Convert to 0-indexed for internal list manipulation if needed, or adjust loop boundaries.
        // If inserting *before* page X (1-indexed), it's at index X-1.
        int insertAtIndex = insertAtPageNumApi - 1;

        try (PDDocument mainDoc = PDDocument.load(mainFile.getInputStream());
             PDDocument pagesToInsertDoc = PDDocument.load(pagesToInsertFile.getInputStream())) {

            int mainDocPages = mainDoc.getNumberOfPages();

            // Validate insertion point
            // It can range from 0 (before first page) to mainDocPages (after last page)
            if (insertAtIndex < 0 || insertAtIndex > mainDocPages) {
                throw new IllegalArgumentException("Insertion page number is out of bounds. Valid range: 1 to " + (mainDocPages + 1));
            }

            // Using a temporary list of pages to build the new document
            List<PDPage> finalPages = new ArrayList<>();

            // 1. Add pages from mainDoc before the insertion point
            for (int i = 0; i < insertAtIndex; i++) {
                finalPages.add(mainDoc.getPage(i));
            }

            // 2. Add all pages from pagesToInsertDoc
            for (PDPage page : pagesToInsertDoc.getPages()) {
                finalPages.add(page); // Directly adding PDPage objects
            }

            // 3. Add remaining pages from mainDoc from the insertion point onwards
            for (int i = insertAtIndex; i < mainDocPages; i++) {
                finalPages.add(mainDoc.getPage(i));
            }

            // Create a new document and add all pages from the final list
            try (PDDocument resultDoc = new PDDocument()) {
                for (PDPage page : finalPages) {
                    resultDoc.addPage(page); // Add page objects. PDFBox handles resource copying if necessary when saving.
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resultDoc.save(baos);
                return baos.toByteArray();
            }
        }
    }

    // Helper to parse page numbers/ranges like "1,3,5-7" into a sorted list of unique 0-indexed pages
    private List<Integer> parsePagesToDelete(String pagesStr, int totalPages) {
        List<Integer> pagesToDelete = new ArrayList<>();
        if (pagesStr == null || pagesStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Page numbers/ranges to delete cannot be empty.");
        }

        String[] ranges = pagesStr.split(",");
        for (String range : ranges) {
            range = range.trim();
            if (range.contains("-")) {
                String[] parts = range.split("-");
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                     throw new IllegalArgumentException("Invalid range format: " + range);
                }
                try {
                    int start = Integer.parseInt(parts[0]) - 1; // 0-indexed
                    int end = Integer.parseInt(parts[1]) - 1;   // 0-indexed

                    if (start < 0 || end < 0 || start > end || end >= totalPages) {
                         throw new IllegalArgumentException("Invalid page range: " + (start+1) + "-" + (end+1) + ". PDF has " + totalPages + " pages.");
                    }
                    for (int i = start; i <= end; i++) {
                        if (!pagesToDelete.contains(i)) {
                            pagesToDelete.add(i);
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid page number in range: " + range);
                }
            } else {
                try {
                    int pageNum = Integer.parseInt(range) - 1; // 0-indexed
                    if (pageNum < 0 || pageNum >= totalPages) {
                        throw new IllegalArgumentException("Invalid page number: " + (pageNum+1) + ". PDF has " + totalPages + " pages.");
                    }
                    if (!pagesToDelete.contains(pageNum)) {
                        pagesToDelete.add(pageNum);
                    }
                } catch (NumberFormatException e) {
                     throw new IllegalArgumentException("Invalid page number: " + range);
                }
            }
        }
        // Sort in descending order for safe deletion
        pagesToDelete.sort(Collections.reverseOrder());
        return pagesToDelete;
    }

    public byte[] deletePages(MultipartFile file, String pageNumbersToDeleteStr) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                // Or return error, or return empty PDF bytes. For now, let's return original.
                System.out.println("PDF has no pages to delete.");
                return file.getBytes();
            }

            List<Integer> pagesToRemove = parsePagesToDelete(pageNumbersToDeleteStr, totalPages);

            if (pagesToRemove.isEmpty()) {
                // This might happen if parsePagesToDelete has issues or if the input string was problematic but didn't throw.
                // Or if valid but non-existent pages were specified and filtered out.
                // For now, assume parsePagesToDelete throws for bad input.
                // If pagesToRemove is empty because no valid pages were found (e.g. "100" for a 10-page PDF),
                // it's not an error, just means no pages to delete.
                 System.out.println("No valid pages specified for deletion or pages already out of bounds.");
                 // We could return original bytes or throw an error based on desired strictness.
                 // For now, let's return original if no valid pages were found to remove.
                 // However, parsePagesToDelete should throw for out-of-bounds, so this path might be less common.
            }

            if (pagesToRemove.size() >= totalPages) {
                throw new IllegalArgumentException("Cannot delete all pages of the document. At least one page must remain, or use a different operation to create an empty PDF.");
            }

            for (Integer pageIndex : pagesToRemove) { // pageIndex is 0-indexed and sorted high-to-low
                document.removePage(pageIndex);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
