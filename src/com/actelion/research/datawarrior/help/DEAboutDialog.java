/*
 * Copyright 2017 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland
 *
 * This file is part of DataWarrior.
 * 
 * DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with DataWarrior.
 * If not, see http://www.gnu.org/licenses/.
 *
 * @author Thomas Sander
 */

package com.actelion.research.datawarrior.help;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.gui.JImagePanelFixedSize;
import com.actelion.research.gui.hidpi.HiDPIHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DEAboutDialog extends JDialog {
	private static final long serialVersionUID = 20140219L;
	private int mMillis;

    public DEAboutDialog(DEFrame owner) {
		super(owner, "About Pyxis SAR Explorer", true);

	    setUndecorated(true);
		getContentPane().add(createImagePanel());

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				dispose();
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				super.keyPressed(e);
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					dispose();
			}
		});

		pack();
		setLocationRelativeTo(owner);
		setVisible(true);
		}

    public DEAboutDialog(DEFrame owner, int millis) {
		super(owner, "About Pyxis SAR Explorer", true);

	    setUndecorated(true);
		getContentPane().add(createImagePanel());

		pack();
		setLocationRelativeTo(owner);

		mMillis = millis;

		new Thread(() -> {
			try {
				Thread.sleep(mMillis);
			}
			catch (Exception e) {}
			dispose();
		}).start();

		setVisible(true);
		}

    /** Shared with the in-process render check: no upstream artwork is rebranded. */
    public static JPanel createImagePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        JLabel logo = new JLabel(new ImageIcon(new ImageIcon(DEAboutDialog.class.getResource("/images/pyxis-logo.png"))
                .getImage().getScaledInstance(422, 80, Image.SCALE_SMOOTH)));
        panel.add(logo);
        panel.add(Box.createVerticalStrut(20));
        JLabel name = new JLabel("Pyxis SAR Explorer");
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        panel.add(name);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Version " + resource("pyxis-version.txt", "development")
                + " | Built " + resource("builtDate.txt", "unknown")));
        panel.add(Box.createVerticalStrut(18));
        panel.add(new JLabel("<html>Pyxis SAR Explorer is a modified version of DataWarrior,<br>"
                + "(C) Thomas Sander and Idorsia Pharmaceuticals Ltd.,<br>"
                + "licensed under the GNU GPL v3</html>"));
        panel.add(Box.createVerticalStrut(18));
        panel.add(new JLabel("Source: https://github.com/kobimic887/pyxis-sar-explorer"));
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Press Escape or click to close"));
        return panel;
    }

    private static String resource(String name, String fallback) {
        try (var input = DEAboutDialog.class.getResourceAsStream("/resources/" + name)) {
            if (input != null)
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException e) { /* Development builds may have no packaging metadata. */ }
        return fallback;
    }
}
