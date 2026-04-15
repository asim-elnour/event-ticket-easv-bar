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

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;


public class TicketPrintService {

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
        String body = "Hello " + safeText(ticket.getCustomerName()) + ",\n\n"
                + "Your ticket is ready.\n"
                + "Ticket code: " + safeText(ticket.getCode()) + "\n"
                + "Event: " + (event == null ? "N/A" : safeText(event.getName())) + "\n\n"
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
        float margin = 48f;
        float y = page.getMediaBox().getHeight() - margin;

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.setNonStrokingColor(30, 30, 30);
            cs.addRect(margin, y - 55f, page.getMediaBox().getWidth() - (2 * margin), 42f);
            cs.fill();

            cs.beginText();
            cs.setNonStrokingColor(255, 255, 255);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 19);
            cs.newLineAtOffset(margin + 12f, y - 28f);
            cs.showText("EVENT TICKET");
            cs.endText();

            y -= 78f;
            y = writeLine(cs, "Event", event == null ? "N/A" : safeText(event.getName()), margin, y);
            y = writeLine(cs, "Location", event == null ? "N/A" : safeText(event.getLocation()), margin, y);
            y = writeLine(cs, "Customer", safeText(ticket.getCustomerName()), margin, y);
            y = writeLine(cs, "Email", safeText(ticket.getCustomerEmail()), margin, y);
            y = writeLine(cs, "Ticket code", safeText(ticket.getCode()), margin, y);
            y = writeLine(cs, "Issued", ticket.getIssuedAt() == null ? "N/A" : ticket.getIssuedAt().toString(), margin, y);
            y = writeLine(cs, "Redeemed", ticket.isRedeemed() ? "Yes" : "No", margin, y);

            BufferedImage barcodeImage = generateBarcode(safeText(ticket.getCode()), BarcodeFormat.CODE_128, 420, 95);
            BufferedImage qrImage = generateBarcode(safeText(ticket.getCode()), BarcodeFormat.QR_CODE, 130, 130);

            PDImageXObject barcode = LosslessFactory.createFromImage(document, barcodeImage);
            PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);

            cs.drawImage(barcode, margin, y - 120f, 360f, 80f);
            cs.drawImage(qr, page.getMediaBox().getWidth() - margin - 130f, y - 145f, 130f, 130f);
        }
    }

    private float writeLine(PDPageContentStream cs, String label, String value, float x, float y) throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(50, 50, 50);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(label + ":");
        cs.endText();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 11);
        cs.newLineAtOffset(x + 80f, y);
        cs.showText(value == null ? "" : value);
        cs.endText();

        return y - 18f;
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

    private String safeText(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
