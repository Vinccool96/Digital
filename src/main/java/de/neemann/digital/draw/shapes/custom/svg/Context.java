/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.draw.shapes.custom.svg;

import de.neemann.digital.draw.graphics.Orientation;
import de.neemann.digital.draw.graphics.Transform;
import de.neemann.digital.draw.graphics.VectorFloat;
import de.neemann.digital.draw.graphics.VectorInterface;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.awt.*;
import java.util.HashMap;
import java.util.StringTokenizer;

class Context {

    private static final HashMap<String, AttrParser> PARSER = new HashMap<>();


    static {
        PARSER.put("transform", (c1, value1) -> c1.tr = Transform.mul(new TransformParser(value1).parse(), c1.tr));
        PARSER.put("fill", (c, value) -> c.fill = getColorFromString(value));
        PARSER.put("fill-opacity", (c, value) -> c.fillOpacity = getFloatFromString(value));
        PARSER.put("stroke", (c, value) -> c.stroke = getColorFromString(value));
        PARSER.put("stroke-opacity", (c, value) -> c.strokeOpacity = getFloatFromString(value));
        PARSER.put("stroke-width", (c, value) -> c.thickness = getFloatFromString(value) + 1);
        PARSER.put("font-size", (c, value) -> c.fontSize = getFloatFromString(value) + 1);
        PARSER.put("style", Context::readStyle);
        PARSER.put("text-anchor", (c, value) -> c.textAnchor = value);
        PARSER.put("fill-rule", (c, value) -> c.fillRuleEvenOdd = value.equalsIgnoreCase("evenodd"));
        PARSER.put("class", Context::evalClass);
    }

    private Transform tr;
    private Color fill;
    private float fillOpacity;
    private Color stroke;
    private float strokeOpacity;
    private float thickness;
    private float fontSize;
    private String textAnchor;
    private boolean fillRuleEvenOdd;
    private HashMap<String, String> classesMap;

    Context() {
        tr = Transform.IDENTITY;
        thickness = 1;
        stroke = Color.BLACK;
        fillOpacity = 1;
        strokeOpacity = 1;
        classesMap = new HashMap<>();
    }

    private Context(Context parent) {
        tr = parent.tr;
        fill = parent.fill;
        fillOpacity = parent.fillOpacity;
        stroke = parent.stroke;
        strokeOpacity = parent.strokeOpacity;
        thickness = parent.thickness;
        fontSize = parent.fontSize;
        textAnchor = parent.textAnchor;
        fillRuleEvenOdd = parent.fillRuleEvenOdd;
        classesMap = new HashMap<>();
        classesMap.putAll(parent.classesMap);
    }

    Context(Context parent, Element element) throws SvgException {
        this(parent);
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node item = attributes.item(i);
            AttrParser p = PARSER.get(item.getNodeName());
            if (p != null)
                p.parse(this, item.getNodeValue().trim());
        }
    }

    static Context readStyle(Context context, String style) throws SvgException {
        StringTokenizer st = new StringTokenizer(style, ";");
        while (st.hasMoreTokens()) {
            String[] t = st.nextToken().split(":");
            if (t.length == 2) {
                AttrParser p = PARSER.get(t[0].trim());
                if (p != null)
                    p.parse(context, t[1].trim());
            }
        }
        return context;
    }

    Transform getTransform() {
        return tr;
    }

    public Color getStroke() {
        return createColor(stroke, strokeOpacity);
    }

    public Color getFilled() {
        return createColor(fill, fillOpacity);
    }

    private static Color createColor(Color color, float opacity) {
        if (color == null)
            return null;
        if (opacity == 1)
            return color;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * opacity));
    }

    public int getThickness() {
        return (int) thickness;
    }

    boolean isFillRuleEvenOdd() {
        return fillRuleEvenOdd;
    }

    Orientation getTextOrientation() {
        if (textAnchor == null)
            return Orientation.LEFTBOTTOM;

        switch (textAnchor) {
            case "end":
                return Orientation.RIGHTBOTTOM;
            case "middle":
                return Orientation.CENTERBOTTOM;
            default:
                return Orientation.LEFTBOTTOM;
        }
    }

    public VectorInterface tr(VectorInterface vector) {
        return vector.transform(tr);
    }

    public VectorInterface v(float x, float y) {
        return new VectorFloat(x, y).transform(tr);
    }

    public VectorInterface v(String xStr, String yStr) {
        float x = xStr.isEmpty() ? 0 : Float.parseFloat(xStr);
        float y = yStr.isEmpty() ? 0 : Float.parseFloat(yStr);
        return v(x, y);
    }

    public float getFontSize() {
        return fontSize;
    }

    void addClasses(String classes) {
        classes = classes.trim();
        while (classes.startsWith(".")) {
            int p1 = classes.indexOf("{");
            int p2 = classes.indexOf("}");
            if (p1 < 0 || p2 < 0)
                return;
            String key = classes.substring(1, p1);
            String val = classes.substring(p1 + 1, p2);
            classesMap.put(key, val);
            classes = classes.substring(p2 + 1).trim();
        }
    }

    String getCssClass(String key) {
        return classesMap.get(key);
    }

    private static void evalClass(Context c, String value) throws SvgException {
        StringTokenizer st = new StringTokenizer(value, ", ");
        while (st.hasMoreTokens()) {
            String cl = st.nextToken();
            String style = c.getCssClass(cl);
            if (style != null)
                readStyle(c, style);
        }
    }

    private interface AttrParser {
        void parse(Context c, String value) throws SvgException;
    }

    private static Color getColorFromString(String v) {
        if (v.equalsIgnoreCase("none"))
            return null;

        try {
            if (v.startsWith("#")) {
                if (v.length() == 4)
                    return new Color(sRGB(v.charAt(1)), sRGB(v.charAt(2)), sRGB(v.charAt(3)));
                else
                    return Color.decode(v);
            } else if (v.startsWith("rgb(")) {
                StringTokenizer st = new StringTokenizer(v.substring(4), " ,)");
                return new Color(rgb(st.nextToken()), rgb(st.nextToken()), rgb(st.nextToken()));
            }

            return (Color) Color.class.getField(v.toLowerCase()).get(null);
        } catch (RuntimeException | IllegalAccessException | NoSuchFieldException e) {
            return Color.BLACK;
        }
    }

    private static int rgb(String s) {
        if (s.endsWith("%"))
            return (int) (Float.parseFloat(s.substring(0, s.length() - 1)) / 100 * 255);
        else
            return Integer.parseInt(s);
    }

    private static int sRGB(char c) {
        int v = Character.digit(c, 16);
        return v * 16 + v;
    }

    private static float getFloatFromString(String inp) {
        inp = inp.replaceAll("[^0-9.]", "");
        if (inp.isEmpty())
            return 1;
        return Float.parseFloat(inp);
    }

}