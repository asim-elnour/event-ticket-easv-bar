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
    private static final Color CODE_PANEL = Color.WHITE;
    private static final Color SECTION_BACKGROUND = new Color(31, 19, 46);
    private static final Color SECTION_BORDER = new Color(79, 56, 116);
    private static final Color DARK_TEXT = new Color(41, 31, 57);
    private static final Color LIGHT_BORDER = new Color(216, 211, 224);
    private static final Color SECTION_TITLE_COLOR = new Color(225, 214, 245);
    private static final Color STATUS_VALID = new Color(52, 168, 83);
    private static final Color STATUS_REDEEMED = new Color(255, 179, 71);
    private static final Color STATUS_REFUNDED = new Color(229, 83, 83);
    private static final float CARD_MARGIN = 40f;
    private static final float CARD_PADDING = 28f;
    private static final float HEADER_HEIGHT = 78f;
    private static final float SECTION_GAP = 16f;
    private static final float DETAIL_LABEL_WIDTH = 82f;
    private static final float DETAIL_FONT_SIZE = 11.5f;
    private static final float DETAIL_LINE_HEIGHT = 16f;
    private static final float DETAIL_ROW_GAP = 7f;
    private static final float DETAILS_QR_GAP = 20f;
    private static final float QR_BOX_SIZE = 164f;
    private static final float QR_IMAGE_SIZE = 132f;
    private static final float TEXT_SECTION_HEIGHT = 78f;
    private static final float BARCODE_BOX_HEIGHT = 156f;
    private static final float BARCODE_IMAGE_WIDTH = 348f;
    private static final float BARCODE_IMAGE_HEIGHT = 80f;

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
        float contentWidth = contentRight - contentLeft;
        float headerBottom = cardTop - HEADER_HEIGHT;
        float bodyTop = headerBottom - CARD_PADDING;
        float barcodeBoxY = cardY + CARD_PADDING;
        float notesBoxY = barcodeBoxY + BARCODE_BOX_HEIGHT + SECTION_GAP;
        float guidanceBoxY = notesBoxY + TEXT_SECTION_HEIGHT + SECTION_GAP;
        float topSectionY = guidanceBoxY + TEXT_SECTION_HEIGHT + SECTION_GAP;
        float topSectionHeight = bodyTop - topSectionY;
        float barcodeBoxWidth = contentWidth;
        float qrBoxX = contentRight - QR_BOX_SIZE;
        float qrBoxY = topSectionY + ((topSectionHeight - QR_BOX_SIZE) / 2f);
        float detailsWidth = qrBoxX - contentLeft - DETAILS_QR_GAP - 16f;

        String eventName = safeText(resolveText(event == null ? null : event.getName(), ticket.getEventName()));
        String eventLocation = safeText(resolveText(event == null ? null : event.getLocation(), ticket.getEventLocation()));
        String eventGuidance = safeText(resolveText(event == null ? null : event.getLocationGuidance(), ticket.getEventGuidance()));
        String eventNotes = safeText(resolveText(event == null ? null : event.getNotes(), ticket.getEventNotes()));
        String eventStart = formatDateTime(resolveDateTime(event == null ? null : event.getStartTime(), ticket.getEventStartTime()));
        String eventEnd = formatDateTime(resolveDateTime(event == null ? null : event.getEndTime(), ticket.getEventEndTime()));
        String customerName = safeText(ticket.getCustomerName());
        String customerEmail = safeText(ticket.getCustomerEmail());
        String issuedAt = formatDateTime(ticket.getIssuedAt());
        String status = safeText(ticket.getStatusLabel());
        String lifecycleLabel = resolveLifecycleLabel(ticket);
        String lifecycleValue = resolveLifecycleValue(ticket);
        String ticketCode = safeText(ticket.getCode());
        String rawCode = ticket.getCode() == null || ticket.getCode().isBlank() ? null : ticket.getCode().trim();

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            fillRect(cs, 0f, 0f, pageWidth, pageHeight, PAGE_BACKGROUND);
            fillRect(cs, cardX, cardY, cardWidth, cardHeight, CARD_BACKGROUND);
            strokeRect(cs, cardX, cardY, cardWidth, cardHeight, CARD_BORDER, 1.4f);
            fillRect(cs, cardX, headerBottom, cardWidth, HEADER_HEIGHT, HEADER_BACKGROUND);

            drawText(cs, "Ticket", contentLeft, cardTop - 34f, PDType1Font.HELVETICA_BOLD, 24f, VALUE_COLOR);
            drawText(cs, "Printed ticket and email attachment", contentLeft, cardTop - 56f,
                    PDType1Font.HELVETICA, 11f, MUTED_COLOR);
            drawStatusPill(cs, status, contentRight, cardTop - 32f);

            fillRect(cs, contentLeft, topSectionY, contentWidth, topSectionHeight, SECTION_BACKGROUND);
            strokeRect(cs, contentLeft, topSectionY, contentWidth, topSectionHeight, SECTION_BORDER, 1f);

            float detailY = topSectionY + topSectionHeight - 24f;
            detailY = writeDetailRow(cs, "Event:", eventName, contentLeft + 18f, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Location:", eventLocation, contentLeft + 18f, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Start:", eventStart, contentLeft + 18f, detailY, detailsWidth, false);
            detailY = writeDetailRow(cs, "End:", eventEnd, contentLeft + 18f, detailY, detailsWidth, false);
            detailY = writeDetailRow(cs, "Customer:", customerName, contentLeft + 18f, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Email:", customerEmail, contentLeft + 18f, detailY, detailsWidth, true);
            detailY = writeDetailRow(cs, "Issued:", issuedAt, contentLeft + 18f, detailY, detailsWidth, false);
            writeDetailRow(cs, lifecycleLabel, lifecycleValue, contentLeft + 18f, detailY, detailsWidth, true);

            fillRect(cs, qrBoxX, qrBoxY, QR_BOX_SIZE, QR_BOX_SIZE, CODE_PANEL);
            strokeRect(cs, qrBoxX, qrBoxY, QR_BOX_SIZE, QR_BOX_SIZE, LIGHT_BORDER, 1f);
            drawText(cs, "QR", qrBoxX + 12f, qrBoxY + QR_BOX_SIZE - 18f, PDType1Font.HELVETICA_BOLD, 10f, new Color(86, 77, 109));
            if (rawCode != null) {
                BufferedImage qrImage = generateBarcode(rawCode, BarcodeFormat.QR_CODE, 140, 140);
                PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);
                cs.drawImage(qr, qrBoxX + ((QR_BOX_SIZE - QR_IMAGE_SIZE) / 2f), qrBoxY + 12f, QR_IMAGE_SIZE, QR_IMAGE_SIZE);
            } else {
                drawCenteredText(cs, "Code unavailable", qrBoxX + (QR_BOX_SIZE / 2f), qrBoxY + (QR_BOX_SIZE / 2f),
                        PDType1Font.HELVETICA_BOLD, 11f, DARK_TEXT);
            }

            drawTextSection(cs, "Guidance", eventGuidance, contentLeft, guidanceBoxY, contentWidth, TEXT_SECTION_HEIGHT);
            drawTextSection(cs, "Notes", eventNotes, contentLeft, notesBoxY, contentWidth, TEXT_SECTION_HEIGHT);

            fillRect(cs, contentLeft, barcodeBoxY, barcodeBoxWidth, BARCODE_BOX_HEIGHT, CODE_PANEL);
            strokeRect(cs, contentLeft, barcodeBoxY, barcodeBoxWidth, BARCODE_BOX_HEIGHT, LIGHT_BORDER, 1f);
            drawText(cs, "Barcode", contentLeft + 16f, barcodeBoxY + BARCODE_BOX_HEIGHT - 22f,
                    PDType1Font.HELVETICA_BOLD, 10f, new Color(86, 77, 109));
            if (rawCode != null) {
                BufferedImage barcodeImage = generateBarcode(rawCode, BarcodeFormat.CODE_128, 420, 95);
                PDImageXObject barcode = LosslessFactory.createFromImage(document, barcodeImage);
                float barcodeX = contentLeft + ((barcodeBoxWidth - BARCODE_IMAGE_WIDTH) / 2f);
                float barcodeY = barcodeBoxY + 44f;
                cs.drawImage(barcode, barcodeX, barcodeY, BARCODE_IMAGE_WIDTH, BARCODE_IMAGE_HEIGHT);
            }
            drawCenteredText(cs, ticketCode, contentLeft + (barcodeBoxWidth / 2f), barcodeBoxY + 20f,
                    PDType1Font.HELVETICA_BOLD, 14f, DARK_TEXT);
        }
    }

    private void drawTextSection(PDPageContentStream cs,
                                 String title,
                                 String value,
                                 float x,
                                 float y,
                                 float width,
                                 float height) throws IOException {
        fillRect(cs, x, y, width, height, SECTION_BACKGROUND);
        strokeRect(cs, x, y, width, height, SECTION_BORDER, 1f);
        drawText(cs, title, x + 16f, y + height - 22f, PDType1Font.HELVETICA_BOLD, 10.5f, SECTION_TITLE_COLOR);

        List<String> lines = wrapText(value, PDType1Font.HELVETICA, 11f, width - 32f, 3);
        float lineY = y + height - 40f;
        for (String line : lines) {
            drawText(cs, line, x + 16f, lineY, PDType1Font.HELVETICA, 11f, VALUE_COLOR);
            lineY -= 14f;
        }
    }

    private float writeDetailRow(PDPageContentStream cs,
                                 String label,
                                 String value,
                                 float x,
                                 float y,
                                 float width,
                                 boolean wrapValue) throws IOException {
        return writeDetailRow(cs, label, value, x, y, width, wrapValue, 0);
    }

    private float writeDetailRow(PDPageContentStream cs,
                                 String label,
                                 String value,
                                 float x,
                                 float y,
                                 float width,
                                 boolean wrapValue,
                                 int maxLines) throws IOException {
        float valueX = x + DETAIL_LABEL_WIDTH;
        float valueWidth = Math.max(120f, width - DETAIL_LABEL_WIDTH);

        drawText(cs, label, x, y, PDType1Font.HELVETICA_BOLD, 12f, LABEL_COLOR);

        if (wrapValue) {
            List<String> lines = maxLines > 0
                    ? wrapText(value, PDType1Font.HELVETICA, DETAIL_FONT_SIZE, valueWidth, maxLines)
                    : wrapText(value, PDType1Font.HELVETICA, DETAIL_FONT_SIZE, valueWidth);
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
            if (word == null || word.isBlank()) {
                continue;
            }

            if (textWidth(font, fontSize, word) > maxWidth) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }

                List<String> chunks = splitTokenToFit(word, font, fontSize, maxWidth);
                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    if (i == chunks.size() - 1) {
                        line.append(chunk);
                    } else {
                        lines.add(chunk);
                    }
                }
                continue;
            }

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

    private List<String> splitTokenToFit(String token,
                                         PDType1Font font,
                                         float fontSize,
                                         float maxWidth) throws IOException {
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        int start = 0;

        while (start < token.length()) {
            int end = findTokenBreakIndex(token, start, font, fontSize, maxWidth);
            if (end <= start) {
                end = Math.min(start + 1, token.length());
            }
            chunks.add(token.substring(start, end));
            start = end;
        }

        return chunks.isEmpty() ? List.of(token) : chunks;
    }

    private int findTokenBreakIndex(String token,
                                    int start,
                                    PDType1Font font,
                                    float fontSize,
                                    float maxWidth) throws IOException {
        StringBuilder builder = new StringBuilder();
        int preferredBreak = -1;

        for (int index = start; index < token.length(); index++) {
            char current = token.charAt(index);
            builder.append(current);

            if (isPreferredBreakCharacter(current)) {
                preferredBreak = index + 1;
            }

            if (textWidth(font, fontSize, builder.toString()) > maxWidth) {
                if (preferredBreak > start) {
                    return preferredBreak;
                }
                return Math.max(start + 1, index);
            }
        }

        return token.length();
    }

    private boolean isPreferredBreakCharacter(char value) {
        return switch (value) {
            case '-', '_', '/', '\\', '.', '@', ':', ',', ';', '#', '=' -> true;
            default -> false;
        };
    }

    private List<String> wrapText(String text,
                                  PDType1Font font,
                                  float fontSize,
                                  float maxWidth,
                                  int maxLines) throws IOException {
        List<String> lines = wrapText(text, font, fontSize, maxWidth);
        if (maxLines <= 0 || lines.size() <= maxLines) {
            return lines;
        }

        java.util.ArrayList<String> limited = new java.util.ArrayList<>(lines.subList(0, maxLines));
        String lastLine = limited.get(maxLines - 1);
        String ellipsis = "...";
        while (!lastLine.isEmpty() && textWidth(font, fontSize, lastLine + ellipsis) > maxWidth) {
            lastLine = lastLine.substring(0, lastLine.length() - 1).trim();
        }
        limited.set(maxLines - 1, lastLine.isEmpty() ? ellipsis : lastLine + ellipsis);
        return limited;
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

    private String resolveText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private LocalDateTime resolveDateTime(LocalDateTime primary, LocalDateTime fallback) {
        return primary != null ? primary : fallback;
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
