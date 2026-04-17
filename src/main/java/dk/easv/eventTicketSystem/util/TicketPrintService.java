package dk.easv.eventTicketSystem.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;


public class TicketPrintService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final Color PAGE_BACKGROUND = new Color(245, 241, 249);
    private static final Color CARD_BACKGROUND = new Color(23, 12, 35);
    private static final Color CARD_BORDER = new Color(70, 42, 111);
    private static final Color HEADER_BACKGROUND = new Color(36, 18, 55);
    private static final Color LABEL_COLOR = new Color(206, 188, 242);
    private static final Color VALUE_COLOR = Color.WHITE;
    private static final Color MUTED_COLOR = new Color(188, 173, 219);
    private static final Color CODE_PANEL = new Color(250, 248, 252);
    private static final Color STATUS_VALID = new Color(52, 168, 83);
    private static final Color STATUS_REDEEMED = new Color(255, 179, 71);
    private static final Color STATUS_REFUNDED = new Color(229, 83, 83);
    private static final float CARD_MARGIN = 40f;
    private static final float CARD_PADDING = 28f;
    private static final float HEADER_HEIGHT = 78f;
    private static final float DETAIL_LABEL_WIDTH = 92f;
    private static final float DETAIL_FONT_SIZE = 13f;
    private static final float DETAIL_LINE_HEIGHT = 18f;
    private static final float DETAIL_ROW_GAP = 10f;
    private static final float DETAILS_QR_GAP = 26f;
    private static final float QR_BOX_SIZE = 164f;
    private static final float QR_IMAGE_SIZE = 136f;
    private static final float BARCODE_BOX_HEIGHT = 176f;
    private static final float BARCODE_IMAGE_WIDTH = 360f;
    private static final float BARCODE_IMAGE_HEIGHT = 82f;

    public Path createTicketPdf(Ticket ticket, Event event) throws IOException {
        Path output = Files.createTempFile("event-ticket-", ".pdf");
        writePdf(ticket, event, output);
        output.toFile().deleteOnExit();
        return output;
    }

    public void openPdfInBrowser(Path pdfPath) throws IOException {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            throw new IOException("Ticket PDF was not generated.");
        }

        Desktop desktop = desktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(pdfPath.toUri());
            return;
        }

        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(pdfPath.toFile());
            return;
        }

        throw new IOException("Opening ticket PDF is not supported on this machine.");
    }

    public void openMailClient(Ticket ticket, Event event, Path pdfPath) throws IOException {
        if (ticket == null || ticket.getCustomerEmail() == null || ticket.getCustomerEmail().isBlank()) {
            throw new IOException("Ticket has no customer email.");
        }
        if (pdfPath == null || !Files.exists(pdfPath)) {
            throw new IOException("Ticket PDF was not generated.");
        }

        String subject = "Your ticket for " + (event == null ? "the event" : event.getName());
        String lifecycleLabel = resolveLifecycleLabel(ticket);
        String lifecycleValue = resolveLifecycleValue(ticket);
        String body = "Hello " + safeText(ticket.getCustomerName()) + ",\n\n"
                + "Your ticket is ready.\n"
                + "Ticket code: " + safeText(ticket.getCode()) + "\n"
                + "Event: " + safeText(event == null ? ticket.getEventName() : event.getName()) + "\n"
                + lifecycleLabel + " " + lifecycleValue + "\n\n"
                + "Best regards,\nEvent Ticket System";

        Desktop desktop = desktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Default mail app is not available.");
        }

        Path draft = createEmailDraftWithAttachment(ticket.getCustomerEmail(), subject, body, pdfPath);
        desktop.open(draft.toFile());
    }

    private void writePdf(Ticket ticket, Event event, Path output) throws IOException {
        if (ticket == null) {
            throw new IOException("No ticket selected.");
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            renderPage(document, page, ticket, event);
            document.save(output.toFile());
        }
    }

    private Path createEmailDraftWithAttachment(String recipient, String subject, String body, Path pdfPath) throws IOException {
        String boundary = "----=_Part_" + System.currentTimeMillis();
        String attachmentName = pdfPath.getFileName().toString();
        String encodedPdf = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(Files.readAllBytes(pdfPath));

        StringBuilder eml = new StringBuilder();
        eml.append("To: ").append(recipient).append("\r\n")
                .append("Subject: ").append(subject).append("\r\n")
                .append("MIME-Version: 1.0\r\n")
                .append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n")
                .append("Content-Transfer-Encoding: 8bit\r\n\r\n")
                .append(body).append("\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: application/pdf; name=\"").append(attachmentName).append("\"\r\n")
                .append("Content-Disposition: attachment; filename=\"").append(attachmentName).append("\"\r\n")
                .append("Content-Transfer-Encoding: base64\r\n\r\n")
                .append(encodedPdf).append("\r\n")
                .append("--").append(boundary).append("--\r\n");

        Path draft = Files.createTempFile("event-ticket-mail-", ".eml");
        Files.writeString(draft, eml.toString(), StandardCharsets.UTF_8);
        draft.toFile().deleteOnExit();
        return draft;
    }

    private void renderPage(PDDocument document, PDPage page, Ticket ticket, Event event) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        float cardX = CARD_MARGIN;
        float cardY = CARD_MARGIN;
        float cardWidth = pageWidth - (2f * CARD_MARGIN);
        float cardHeight = pageHeight - (2f * CARD_MARGIN);
        float cardTop = cardY + cardHeight;
        float contentLeft = cardX + CARD_PADDING;
        float contentRight = cardX + cardWidth - CARD_PADDING;
        float headerBottom = cardTop - HEADER_HEIGHT;
        float bodyTop = headerBottom - CARD_PADDING;
        float barcodeBoxY = cardY + CARD_PADDING;
        float barcodeBoxWidth = cardWidth - (2f * CARD_PADDING);
        float barcodeBoxTop = barcodeBoxY + BARCODE_BOX_HEIGHT;
        float qrBoxX = contentRight - QR_BOX_SIZE;
        float qrBoxY = bodyTop - QR_BOX_SIZE;
        float detailsWidth = qrBoxX - contentLeft - DETAILS_QR_GAP;

        String eventName = safeText(event == null ? ticket.getEventName() : event.getName());
        String customerName = safeText(ticket.getCustomerName());
        String customerEmail = safeText(ticket.getCustomerEmail());
        String issuedAt = formatDateTime(ticket.getIssuedAt());
        String status = safeText(ticket.getStatusLabel());
        String lifecycleLabel = resolveLifecycleLabel(ticket);
        String lifecycleValue = resolveLifecycleValue(ticket);

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            fillRect(cs, 0f, 0f, pageWidth, pageHeight, PAGE_BACKGROUND);
            fillRect(cs, cardX, cardY, cardWidth, cardHeight, CARD_BACKGROUND);
            strokeRect(cs, cardX, cardY, cardWidth, cardHeight, CARD_BORDER, 1.4f);
            fillRect(cs, cardX, headerBottom, cardWidth, HEADER_HEIGHT, HEADER_BACKGROUND);

            drawText(cs, "Ticket", contentLeft, cardTop - 34f, PDType1Font.HELVETICA_BOLD, 24f, VALUE_COLOR);
            drawText(cs, "Printed ticket and email attachment", contentLeft, cardTop - 56f,
                    PDType1Font.HELVETICA, 11f, MUTED_COLOR);
            drawStatusPill(cs, status, contentRight, cardTop - 32f);

            float detailY = bodyTop;
            detailY = writeDetailRow(cs, "Event:", eventName, contentLeft, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Customer:", customerName, contentLeft, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Email:", customerEmail, contentLeft, detailY, detailsWidth, false);
            detailY = writeDetailRow(cs, "Issued:", issuedAt, contentLeft, detailY, detailsWidth, false);
            writeDetailRow(cs, lifecycleLabel, lifecycleValue, contentLeft, detailY, detailsWidth, true);

            BufferedImage barcodeImage = generateBarcode(safeText(ticket.getCode()), BarcodeFormat.CODE_128, 420, 95);
            BufferedImage qrImage = generateBarcode(safeText(ticket.getCode()), BarcodeFormat.QR_CODE, 140, 140);

            PDImageXObject barcode = LosslessFactory.createFromImage(document, barcodeImage);
            PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);

            fillRect(cs, qrBoxX, qrBoxY, QR_BOX_SIZE, QR_BOX_SIZE, CODE_PANEL);
            strokeRect(cs, qrBoxX, qrBoxY, QR_BOX_SIZE, QR_BOX_SIZE, new Color(216, 211, 224), 1f);
            drawText(cs, "QR", qrBoxX + 12f, qrBoxY + QR_BOX_SIZE - 18f, PDType1Font.HELVETICA_BOLD, 10f, new Color(86, 77, 109));
            cs.drawImage(qr, qrBoxX + ((QR_BOX_SIZE - QR_IMAGE_SIZE) / 2f), qrBoxY + 12f, QR_IMAGE_SIZE, QR_IMAGE_SIZE);

            fillRect(cs, contentLeft, barcodeBoxY, barcodeBoxWidth, BARCODE_BOX_HEIGHT, CODE_PANEL);
            strokeRect(cs, contentLeft, barcodeBoxY, barcodeBoxWidth, BARCODE_BOX_HEIGHT, new Color(216, 211, 224), 1f);
            float barcodeX = contentLeft + ((barcodeBoxWidth - BARCODE_IMAGE_WIDTH) / 2f);
            float barcodeY = barcodeBoxTop - 102f;
            cs.drawImage(barcode, barcodeX, barcodeY, BARCODE_IMAGE_WIDTH, BARCODE_IMAGE_HEIGHT);
            drawCenteredText(cs, safeText(ticket.getCode()), contentLeft + (barcodeBoxWidth / 2f), barcodeBoxY + 26f,
                    PDType1Font.HELVETICA_BOLD, 14f, new Color(41, 31, 57));
        }
    }

    private float writeDetailRow(PDPageContentStream cs,
                                 String label,
                                 String value,
                                 float x,
                                 float y,
                                 float width,
                                 boolean wrapValue) throws IOException {
        float valueX = x + DETAIL_LABEL_WIDTH;
        float valueWidth = Math.max(120f, width - DETAIL_LABEL_WIDTH);

        drawText(cs, label, x, y, PDType1Font.HELVETICA_BOLD, 12f, LABEL_COLOR);

        if (wrapValue) {
            List<String> lines = wrapText(value, PDType1Font.HELVETICA, DETAIL_FONT_SIZE, valueWidth);
            float lineY = y;
            for (String line : lines) {
                drawText(cs, line, valueX, lineY, PDType1Font.HELVETICA, DETAIL_FONT_SIZE, VALUE_COLOR);
                lineY -= DETAIL_LINE_HEIGHT;
            }
            return y - (Math.max(1, lines.size()) * DETAIL_LINE_HEIGHT) - DETAIL_ROW_GAP;
        }

        float fittedSize = fitFontSize(value, PDType1Font.HELVETICA, DETAIL_FONT_SIZE, 10.5f, valueWidth);
        drawText(cs, value, valueX, y, PDType1Font.HELVETICA, fittedSize, VALUE_COLOR);
        return y - DETAIL_LINE_HEIGHT - DETAIL_ROW_GAP;
    }

    private BufferedImage generateBarcode(String value, BarcodeFormat format, int width, int height) throws IOException {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int rgb = matrix.get(x, y) ? 0x000000 : 0xFFFFFF;
                    image.setRGB(x, y, rgb);
                }
            }
            return image;
        } catch (Exception ex) {
            throw new IOException("Could not generate barcode image.", ex);
        }
    }

    private Desktop desktop() throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Desktop integrations are not supported.");
        }
        return Desktop.getDesktop();
    }

    private void drawStatusPill(PDPageContentStream cs, String status, float rightX, float baselineY) throws IOException {
        String safeStatus = safeText(status);
        Color fill = switch (safeStatus.toLowerCase(Locale.ROOT)) {
            case "redeemed" -> STATUS_REDEEMED;
            case "refunded" -> STATUS_REFUNDED;
            default -> STATUS_VALID;
        };

        float fontSize = 11f;
        float textWidth = textWidth(PDType1Font.HELVETICA_BOLD, fontSize, safeStatus);
        float pillWidth = textWidth + 28f;
        float pillHeight = 22f;
        float x = rightX - pillWidth;
        float y = baselineY - 10f;

        fillRect(cs, x, y, pillWidth, pillHeight, fill);
        drawCenteredText(cs, safeStatus, x + (pillWidth / 2f), y + 6.5f, PDType1Font.HELVETICA_BOLD, fontSize, VALUE_COLOR);
    }

    private void drawText(PDPageContentStream cs,
                          String text,
                          float x,
                          float y,
                          PDType1Font font,
                          float fontSize,
                          Color color) throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text);
        cs.endText();
    }

    private void drawCenteredText(PDPageContentStream cs,
                                  String text,
                                  float centerX,
                                  float y,
                                  PDType1Font font,
                                  float fontSize,
                                  Color color) throws IOException {
        float width = textWidth(font, fontSize, text);
        drawText(cs, text, centerX - (width / 2f), y, font, fontSize, color);
    }

    private void fillRect(PDPageContentStream cs, float x, float y, float width, float height, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, y, width, height);
        cs.fill();
    }

    private void strokeRect(PDPageContentStream cs,
                            float x,
                            float y,
                            float width,
                            float height,
                            Color color,
                            float lineWidth) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(lineWidth);
        cs.addRect(x, y, width, height);
        cs.stroke();
    }

    private float fitFontSize(String text, PDType1Font font, float preferred, float minimum, float maxWidth) throws IOException {
        float size = preferred;
        while (size > minimum && textWidth(font, size, text) > maxWidth) {
            size -= 0.5f;
        }
        return size;
    }

    private float textWidth(PDType1Font font, float fontSize, String text) throws IOException {
        String safe = text == null ? "" : text;
        return font.getStringWidth(safe) / 1000f * fontSize;
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        String safe = safeText(text);
        if (textWidth(font, fontSize, safe) <= maxWidth) {
            return List.of(safe);
        }

        List<String> words = List.of(safe.split("\\s+"));
        StringBuilder line = new StringBuilder();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                continue;
            }

            lines.add(word);
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines.isEmpty() ? List.of(safe) : lines;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "Not set";
        }
        return DATE_TIME_FORMAT.format(value);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "Not set" : value.trim();
    }

    private String resolveLifecycleLabel(Ticket ticket) {
        if (ticket != null && ticket.isRefunded()) {
            return "Refunded at:";
        }
        if (ticket != null && ticket.isRedeemed()) {
            return "Redeemed at:";
        }
        return "Status:";
    }

    private String resolveLifecycleValue(Ticket ticket) {
        if (ticket == null) {
            return "Not set";
        }
        if (ticket.isRefunded()) {
            return formatDateTime(ticket.getRefundedAt());
        }
        if (ticket.isRedeemed()) {
            return formatDateTime(ticket.getRedeemedAt());
        }
        return "Valid";
    }
}
