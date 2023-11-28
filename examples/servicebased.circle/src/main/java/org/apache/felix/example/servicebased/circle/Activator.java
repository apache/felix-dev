/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.example.servicebased.circle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Ellipse2D;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.swing.ImageIcon;
import org.apache.felix.example.servicebased.host.service.SimpleShape;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * This class implements a simple bundle activator for the circle
 * <tt>SimpleShape</tt> service. This activator simply creates an instance
 * of the circle service object and registers it with the service registry
 * along with the service properties indicating the service's name and icon.
**/
public class Activator implements BundleActivator
{
    private BundleContext m_context = null;

    /**
     * Implements the <tt>BundleActivator.start()</tt> method, which
     * registers the circle <tt>SimpleShape</tt> service.
     * @param context The context for the bundle.
    **/
    public void start(BundleContext context)
    {
        m_context = context;
        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        dict.put(SimpleShape.NAME_PROPERTY, "Circle");
        dict.put(SimpleShape.ICON_PROPERTY,
            new ImageIcon(this.getClass().getResource("circle.png")));
        m_context.registerService(
            SimpleShape.class.getName(), new Circle(), dict);
    }

    /**
     * Implements the <tt>BundleActivator.start()</tt> method, which
     * does nothing.
     * @param context The context for the bundle.
    **/
    public void stop(BundleContext context)
    {
    }

    /**
     * This inner class implements the circle <tt>SimpleShape</tt> service.
     * It simply provides a <tt>draw()</tt> that paints a circle.
    **/
    public static class Circle implements SimpleShape
    {
        /**
         * Implements the <tt>SimpleShape.draw()</tt> method for painting
         * the shape.
         * @param g2 The graphics object used for painting.
         * @param p The position to paint the triangle.
        **/
        public void draw(Graphics2D g2, Point p)
        {
            int x = p.x - 25;
            int y = p.y - 25;
            GradientPaint gradient = new GradientPaint(
                x, y, Color.RED, x + 50, y, Color.WHITE);
            g2.setPaint(gradient);
            g2.fill(new Ellipse2D.Double(x, y, 50, 50));
            BasicStroke wideStroke = new BasicStroke(2.0f);
            g2.setColor(Color.black);
            g2.setStroke(wideStroke);
            g2.draw(new Ellipse2D.Double(x, y, 50, 50));
        }
    }
}