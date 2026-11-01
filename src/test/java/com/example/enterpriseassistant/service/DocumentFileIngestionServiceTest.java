package com.example.enterpriseassistant.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.enterpriseassistant.exception.InvalidDocumentException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DocumentFileIngestionServiceTest {
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentIngestionService documentService = new DocumentIngestionService(vectorStore,
            TokenTextSplitter.builder().withChunkSize(100).withMinChunkSizeChars(20)
                    .withMinChunkLengthToEmbed(0).build());
    private final DocumentFileIngestionService service = new DocumentFileIngestionService(documentService);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ingestsUtf8TextAndAddsFilenameMetadataToChunks() {
        var file = new MockMultipartFile("file", "C:\\uploads\\benefits.TXT", "text/plain",
                ("Employees receive 20 vacation days. Requests are made ten days in advance. ").repeat(8)
                        .getBytes(StandardCharsets.UTF_8));

        var result = service.ingest(file);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(result.filename()).isEqualTo("benefits.TXT");
        assertThat(result.fileType()).isEqualTo("txt");
        assertThat(result.chunkCount()).isEqualTo(captor.getValue().size()).isPositive();
        for (int index = 0; index < captor.getValue().size(); index++) {
            assertThat(captor.getValue().get(index).getMetadata())
                    .containsEntry("source", "file-upload")
                    .containsEntry("sourceFilename", "benefits.TXT")
                    .containsEntry("fileType", "txt")
                    .containsEntry("chunkIndex", index);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void extractsPdfTextAndStoresItThroughSharedVectorStore() throws IOException {
        byte[] pdfBytes = pdfWithText("Employees may work remotely three days each week.");
        var file = new MockMultipartFile("file", "remote-work.pdf", "application/pdf", pdfBytes);

        var result = service.ingest(file);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(result.filename()).isEqualTo("remote-work.pdf");
        assertThat(result.fileType()).isEqualTo("pdf");
        assertThat(captor.getValue()).isNotEmpty();
        assertThat(captor.getValue().getFirst().getText()).contains("Employees may work remotely");
        assertThat(captor.getValue().getFirst().getMetadata()).containsEntry("sourceFilename", "remote-work.pdf")
                .containsEntry("fileType", "pdf");
    }

    @Test
    void rejectsUnsupportedExtension() {
        var file = new MockMultipartFile("file", "policy.docx", "application/octet-stream", new byte[] {1});
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Only PDF and TXT files are supported.");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Choose a non-empty PDF or TXT file.");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsBlankExtractedText() {
        var file = new MockMultipartFile("file", "blank.txt", "text/plain", "  \r\n\t".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("The uploaded document contains no extractable text.");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws IOException {
        byte[] pdfBytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            pdfBytes = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdfBytes);
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("The uploaded document contains no extractable text.");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsInvalidUtf8Text() {
        var file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("TXT files must use UTF-8 encoding.");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsFilesOverTenMegabytes() {
        var file = new MockMultipartFile("file", "large.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("The file exceeds the 10 MB upload limit.");
        verifyNoInteractions(vectorStore);
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
