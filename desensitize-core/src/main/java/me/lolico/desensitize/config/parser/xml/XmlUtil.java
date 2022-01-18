package me.lolico.desensitize.config.parser.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class XmlUtil {

    /**
     * 从流中读取xml文档。
     */
    public static Document doc(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringComments(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(is);
    }

    /**
     * 获取xml文档根节点
     */
    public static Element root(InputStream is) throws Exception {
        return doc(is).getDocumentElement();
    }

    /**
     * 获取节点下的第一层子节点。
     *
     * @param parent  父节点
     * @param tagName 子节点名
     */
    public static List<Element> elements(Element parent, String tagName) {
        if (parent == null || !parent.hasChildNodes()) {
            return Collections.emptyList();
        }

        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String childTagName = childElement.getLocalName();

                if (tagName.equals(childTagName)) {
                    elements.add(childElement);
                }
            }
        }
        return elements;
    }
}