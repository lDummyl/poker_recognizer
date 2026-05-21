package com.poker.recognizer.service;

import com.poker.recognizer.model.CardDetectionResult;
import com.poker.recognizer.model.CardTemplate;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RotatedRect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2HSV;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_LIST;
import static org.bytedeco.opencv.global.opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY_INV;

public class CardRecognitionService {

    private static final Logger log = Logger.getLogger(CardRecognitionService.class.getName());

    private static final double CARD_MIN_AREA_RATIO = 0.0005;
    private static final double CARD_MAX_AREA_RATIO = 0.90;
    private static final double CARD_MIN_ASPECT = 1.1;
    private static final double CARD_MAX_ASPECT = 4.0;
    private static final double MATCH_THRESHOLD = 0.25;

    private final CardTemplateGenerator templateGenerator;
    private List<CardTemplate> templates;
    private List<Mat> templateMats;

    public CardRecognitionService(CardTemplateGenerator templateGenerator) {
        this.templateGenerator = templateGenerator;
        init();
    }

    private void init() {
        templates = templateGenerator.generateAll();
        templateMats = new ArrayList<>();
        for (CardTemplate t : templates) {
            Mat mat = opencv_imgcodecs.imdecode(
                    new Mat(t.getImageBytes()), opencv_imgcodecs.IMREAD_GRAYSCALE);
            templateMats.add(mat);
        }
        log.info("Loaded " + templateMats.size() + " card templates for matching");
    }

    public List<CardDetectionResult> detectCards(byte[] imageBytes) {
        List<CardDetectionResult> results = new ArrayList<>();

        Mat src = opencv_imgcodecs.imdecode(
                new Mat(imageBytes), opencv_imgcodecs.IMREAD_COLOR);
        if (src.empty()) {
            log.warning("Empty image");
            return results;
        }

        if (Math.max(src.cols(), src.rows()) < 500) {
            double scale = 800.0 / Math.max(src.cols(), src.rows());
            int newW = (int) (src.cols() * scale);
            int newH = (int) (src.rows() * scale);
            Mat resized = new Mat();
            opencv_imgproc.resize(src, resized, new Size(newW, newH));
            src = resized;
        }

        double imageArea = src.cols() * src.rows();
        log.info("Processing image: " + src.cols() + "x" + src.rows() + ", area=" + imageArea);

        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, COLOR_BGR2GRAY);

        Mat blurred = new Mat();
        opencv_imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);

        Mat binary = new Mat();
        opencv_imgproc.adaptiveThreshold(blurred, binary, 255,
                ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY_INV, 75, 10);

        MatVector contours = new MatVector();
        opencv_imgproc.findContours(binary, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);
        log.info("Found " + contours.size() + " contours on adaptive binary");

        Mat hsv = new Mat();
        opencv_imgproc.cvtColor(src, hsv, COLOR_BGR2HSV);

        int cardIndex = 0;
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = opencv_imgproc.contourArea(contour);

            double areaRatio = area / imageArea;
            if (areaRatio < CARD_MIN_AREA_RATIO || areaRatio > CARD_MAX_AREA_RATIO) {
                continue;
            }

            RotatedRect rotatedRect = opencv_imgproc.minAreaRect(contour);
            double rotW = rotatedRect.size().width();
            double rotH = rotatedRect.size().height();
            double aspectRatio = Math.max(rotW, rotH) / Math.min(rotW, rotH);

            if (aspectRatio < CARD_MIN_ASPECT || aspectRatio > CARD_MAX_ASPECT) {
                continue;
            }

            Rect boundingRect = opencv_imgproc.boundingRect(contour);
            double rectArea = boundingRect.width() * boundingRect.height();
            double fillRatio = area / rectArea;
            if (fillRatio < 0.4) {
                continue;
            }

            String suitColor = detectSuitColor(src, hsv, boundingRect);
            CardTemplate bestMatch = matchCorner(gray, boundingRect);

            CardDetectionResult result = new CardDetectionResult();
            result.setCardIndex(cardIndex++);
            result.setX(boundingRect.x());
            result.setY(boundingRect.y());
            result.setWidth(boundingRect.width());
            result.setHeight(boundingRect.height());
            result.setAreaRatio(Math.round(areaRatio * 10000.0) / 10000.0);
            result.setAspectRatio(Math.round(aspectRatio * 100.0) / 100.0);
            result.setSuitColor(suitColor);

            if (bestMatch != null) {
                result.setRank(bestMatch.getRank());
                result.setSuit(bestMatch.getSuit());
                result.setLabel(bestMatch.getLabel());
            } else {
                result.setRank("?");
                result.setSuit("?");
                result.setLabel("UNKNOWN");
            }

            results.add(result);
        }

        results.sort(Comparator.comparingInt(CardDetectionResult::getY)
                .thenComparingInt(CardDetectionResult::getX));

        for (int j = 0; j < results.size(); j++) {
            results.get(j).setCardIndex(j);
        }

        log.info("=== Detected " + results.size() + " cards ===");
        return results;
    }

    private CardTemplate matchCorner(Mat graySrc, Rect boundingRect) {
        int cornerW = (int) (boundingRect.width() * 0.22);
        int cornerH = (int) (boundingRect.height() * 0.20);
        int cx = Math.max(0, boundingRect.x());
        int cy = Math.max(0, boundingRect.y());
        cornerW = Math.min(cornerW, graySrc.cols() - cx);
        cornerH = Math.min(cornerH, graySrc.rows() - cy);

        if (cornerW < 10 || cornerH < 10) {
            return null;
        }

        Rect cornerRect = new Rect(cx, cy, cornerW, cornerH);
        Mat corner = graySrc.apply(cornerRect);
        Mat cornerResized = new Mat();
        opencv_imgproc.resize(corner, cornerResized, new Size(50, 70));

        CardTemplate bestTemplate = null;
        double bestScore = -1;

        for (int t = 0; t < templateMats.size(); t++) {
            Mat resultMat = new Mat();
            opencv_imgproc.matchTemplate(cornerResized, templateMats.get(t), resultMat,
                    opencv_imgproc.TM_CCOEFF_NORMED);
            double[] minMax = new double[2];
            opencv_core.minMaxLoc(resultMat, minMax);
            double score = minMax[1];
            if (score > bestScore) {
                bestScore = score;
                bestTemplate = templates.get(t);
            }
        }

        if (bestScore >= MATCH_THRESHOLD && bestTemplate != null) {
            log.fine("Card matched: " + bestTemplate.getLabel() + " (score=" + String.format("%.2f", bestScore) + ")");
            return bestTemplate;
        }
        return null;
    }

    private String detectSuitColor(Mat src, Mat hsv, Rect boundingRect) {
        int cornerW = (int) (boundingRect.width() * 0.20);
        int cornerH = (int) (boundingRect.height() * 0.15);
        int cx = Math.max(0, boundingRect.x());
        int cy = Math.max(0, boundingRect.y());
        cornerW = Math.min(cornerW, src.cols() - cx);
        cornerH = Math.min(cornerH, src.rows() - cy);

        if (cornerW < 5 || cornerH < 5) {
            return "UNKNOWN";
        }

        Rect cornerRect = new Rect(cx, cy, cornerW, cornerH);
        Mat cornerHsv = hsv.apply(cornerRect);
        Scalar meanColor = opencv_core.mean(cornerHsv);
        double hue = meanColor.get(0);
        double saturation = meanColor.get(1);

        if (saturation > 15 && ((hue >= 0 && hue < 30) || hue > 150)) {
            return "RED";
        }
        if (saturation <= 15 && hue >= 0) {
            return "BLACK";
        }
        return "UNKNOWN";
    }
}
