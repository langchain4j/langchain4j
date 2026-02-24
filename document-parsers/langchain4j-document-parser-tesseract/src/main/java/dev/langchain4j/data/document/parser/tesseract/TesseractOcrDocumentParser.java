package dev.langchain4j.data.document.parser.tesseract;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.internal.Utils.*;

public class TesseractOcrDocumentParser implements DocumentParser {
    private static final String DEFAULT_LANGUAGE = "eng";

    private static final String DEFAULT_DATAPATH = "src/main/resource/tessdata";
    private String language;

    private String dataPath;
    private Tesseract tesseract;

    public TesseractOcrDocumentParser() {
        this(null, null);
    }

    public TesseractOcrDocumentParser(String language) {
        this(language, null);
    }

    public TesseractOcrDocumentParser(String language, String dataPath) {
        this.language = getOrDefault(language, () -> DEFAULT_LANGUAGE);
        if  (isNullOrBlank(dataPath) && isNotNullOrBlank(System.getenv("TESSDATA_PREFIX")))
        {
            this.dataPath = System.getenv("TESSDATA_PREFIX");
        }
        else
        {
            this.dataPath = getOrDefault(dataPath, () -> DEFAULT_DATAPATH);
        }
        Tesseract tesseract = new Tesseract();
        tesseract.setLanguage(this.language);
        tesseract.setDatapath(this.dataPath);
        this.tesseract = tesseract;
    }

    @Override
    public Document parse(InputStream inputStream) {
        try {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            String text = this.tesseract.doOCR(bufferedImage);
            return Document.from(text);
        } catch (IOException | TesseractException e) {
            throw new RuntimeException(e);
        }
    }
}