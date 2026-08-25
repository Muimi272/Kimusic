package club.muimi.kimusic.view.component;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SvgIcon extends StackPane {
    private static final Map<String, IconData> CACHE = new ConcurrentHashMap<>();

    private String resource;
    private double size = 18;

    public SvgIcon() {
        getStyleClass().add("svg-icon");
        setMouseTransparent(true);
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
        render();
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
        render();
    }

    private void render() {
        getChildren().clear();
        if (resource == null || resource.isBlank()) {
            return;
        }
        IconData data = CACHE.computeIfAbsent(resource, SvgIcon::load);
        Group group = new Group();
        for (PathData pathData : data.paths()) {
            SVGPath path = new SVGPath();
            path.setContent(pathData.content());
            path.getStyleClass().add(pathData.filled() ? "svg-fill" : "svg-stroke");
            group.getChildren().add(path);
        }
        double scale = size / Math.max(data.width(), data.height());
        group.setScaleX(scale);
        group.setScaleY(scale);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().add(group);
    }

    private static IconData load(String resource) {
        try (InputStream input = SvgIcon.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("SVG resource not found: " + resource);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(input);
            Element svg = document.getDocumentElement();
            double[] viewBox = parseViewBox(svg.getAttribute("viewBox"));
            NodeList pathNodes = svg.getElementsByTagName("path");
            List<PathData> paths = new ArrayList<>();
            for (int index = 0; index < pathNodes.getLength(); index++) {
                Element path = (Element) pathNodes.item(index);
                String content = path.getAttribute("d");
                if (!content.isBlank()) {
                    paths.add(new PathData(content, !"none".equalsIgnoreCase(path.getAttribute("fill"))));
                }
            }
            return new IconData(viewBox[2], viewBox[3], List.copyOf(paths));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load SVG icon " + resource, exception);
        }
    }

    private static double[] parseViewBox(String value) {
        String[] parts = value.strip().split("[ ,]+");
        if (parts.length != 4) {
            return new double[]{0, 0, 24, 24};
        }
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
    }

    private record IconData(double width, double height, List<PathData> paths) {
    }

    private record PathData(String content, boolean filled) {
    }
}
