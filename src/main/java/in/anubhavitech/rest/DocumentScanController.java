package in.anubhavitech.rest;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import net.sourceforge.tess4j.Tesseract;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin
public class DocumentScanController {

    private static final String DEBUG_DIR = "/tmp/mrz-debug/";

    @PostMapping("/document")
    public ResponseEntity<Map<String, String>> scanDocument(
            @RequestParam("file") MultipartFile file) throws Exception {

        Map<String, String> response = new HashMap<>();

        System.out.println("\n==== RECEIVED FILE: " + file.getOriginalFilename() + " ====");

        // ------------------ Load Image ------------------
        BufferedImage original = ImageIO.read(file.getInputStream());
        if (original == null) {
            return bad("Unsupported or corrupt image file");
        }

        int ow = original.getWidth();
        int oh = original.getHeight();
        System.out.println("Original size: " + ow + "x" + oh);

        if (ow < 600 || oh < 600) {
            return bad("Image resolution too low. Please upload a clearer photo.");
        }

        // Save original for debug
        saveDebug(original, "01_original.png");

        // ------------------ Crop MRZ ------------------
        BufferedImage mrz = cropMRZ(original);
        saveDebug(mrz, "02_mrz_crop.png");

        System.out.println("MRZ crop size: " + mrz.getWidth() + "x" + mrz.getHeight());

        if (mrz.getHeight() < 120) {
            return bad("MRZ area too small. Retake photo with full bottom visible.");
        }

        // ------------------ Preprocess ------------------
        BufferedImage processed = preprocessMRZ(mrz);
        saveDebug(processed, "03_mrz_processed.png");

        // ------------------ OCR ------------------
        Tesseract tesseract = new Tesseract();
        String tessPath = "/usr/local/share/tessdata";
        tesseract.setDatapath(tessPath);
        tesseract.setLanguage("ocrb");
        tesseract.setPageSegMode(4); // SINGLE COLUMN
        tesseract.setOcrEngineMode(1); // LSTM
        tesseract.setTessVariable(
                "tessedit_char_whitelist",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
        );

        File ocrFile = File.createTempFile("ocr-", ".png");
        ImageIO.write(processed, "png", ocrFile);

        String rawText = tesseract.doOCR(ocrFile)
                .replaceAll("\\s+", "")
                .trim();

        ocrFile.delete();

        System.out.println("==== OCR RAW ====");
        System.out.println(rawText);
        System.out.println("================");

        if (rawText.length() < 80 || !rawText.contains("<<")) {
            response.put("rawText", rawText);
            response.put("warning",
                    "MRZ not detected clearly. Please retake image with better lighting.");
            return ResponseEntity.ok(response);
        }

        // ------------------ Parse ------------------
        Map<String, String> parsed = parseMRZ(rawText);
        parsed.put("rawText", rawText);

        System.out.println("==== PARSED MRZ ====");
        parsed.forEach((k, v) -> System.out.println(k + ": " + v));
        System.out.println("===================");

        return ResponseEntity.ok(parsed);
    }

    // =================================================
    // ================= HELPERS =======================
    // =================================================

    private BufferedImage cropMRZ(BufferedImage image) {
        int h = image.getHeight();
        int w = image.getWidth();

        // Airport-style: bottom ~18%
        int mrzHeight = (int) (h * 0.18);
        int y = h - mrzHeight;

        return image.getSubimage(0, y, w, mrzHeight);
    }

    private BufferedImage preprocessMRZ(BufferedImage image) {
        BufferedImage gray = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Strong binarization for MRZ
        RescaleOp rescale = new RescaleOp(3.0f, -250, null);
        rescale.filter(gray, gray);

        // Scale up (OCR-B likes big text)
        int w = gray.getWidth() * 3;
        int h = gray.getHeight() * 3;

        BufferedImage resized = new BufferedImage(
                w, h, BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g2 = resized.createGraphics();
        g2.drawImage(gray, 0, 0, w, h, null);
        g2.dispose();

        return resized;
    }

    private Map<String, String> parseMRZ(String text) {
        Map<String, String> data = new HashMap<>();

        String line1 = text.substring(0, 44);
        String line2 = text.substring(44, 88);

        data.put("documentType", line1.substring(0, 1));
        data.put("issuingCountry", line1.substring(2, 5));

        String[] names = line1.substring(5).split("<<");
        data.put("lastName", names[0].replace("<", " ").trim());
        data.put("firstName",
                names.length > 1 ? names[1].replace("<", " ").trim() : "");

        data.put("passportNumber",
                line2.substring(0, 9).replace("<", "").trim());
        data.put("nationality", line2.substring(10, 13));
        data.put("birthDate", line2.substring(13, 19));
        data.put("sex", line2.substring(20, 21));
        data.put("expiryDate", line2.substring(21, 27));
        data.put("personalNumber",
                line2.substring(28, 42).replace("<", "").trim());

        return data;
    }

    private ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(
                Map.of("error", msg)
        );
    }

    private void saveDebug(BufferedImage img, String name) {
        try {
            new File(DEBUG_DIR).mkdirs();
            ImageIO.write(img, "png", new File(DEBUG_DIR + name));
        } catch (Exception ignored) {}
    }
}
