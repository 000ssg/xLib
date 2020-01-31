/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.common;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 *
 * @author sesidoro
 */
public class ImagingTools {
    public static int MAX_IMAGE_WIDTH = 3000;
    public static int MAX_IMAGE_HEIGHT = 3000;

    public static byte[] scaleImageTo(URL url, Integer width, Integer height, String format, Map<String, byte[]> cache) {
        if (format == null) {
            format = "PNG";
        }
        String key = url + "?w=" + width + "&h=" + height + "&f=" + format;
        if (cache != null && cache.containsKey(key)) {
            return cache.get(key);
        }
        ImageIcon img = new ImageIcon(url);
        double aspect = 1.0 * img.getIconWidth() / img.getIconHeight();
        if (width != null && width > MAX_IMAGE_WIDTH) {
            width = MAX_IMAGE_WIDTH;
        }
        if (height != null && height > MAX_IMAGE_HEIGHT) {
            height = MAX_IMAGE_HEIGHT;
        }
        if (width == null) {
            if (height != null) {
                width = (int) (height / aspect);
            } else {
                width = img.getIconWidth();
                height = img.getIconHeight();
            }
        }
        if (height == null) {
            height = (int) (width * aspect);
        }
        double scaleX = 1.0 * width / img.getIconWidth();
        double scaleY = 1.0 * height / img.getIconHeight();
        double scale = Math.min(scaleX, scaleY);
        int dX = 1;//(int) Math.round(this.getWidth() * scale);
        int dY = 1;//(int) Math.round(this.getHeight() * scale);
        if (scale != scaleX) {
            width = (int) (width / scaleX * scale);
        }
        if (scale != scaleY) {
            height = (int) (height / scaleY * scale);
        }
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = bi.createGraphics();
        gr.scale(scale, scale);
        gr.drawImage(img.getImage(), dX / 2, dY / 2, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ImageIO.write(bi, format, bos);
            if (cache != null) {
                cache.put(key, bos.toByteArray());
            }
            return bos.toByteArray();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

}
