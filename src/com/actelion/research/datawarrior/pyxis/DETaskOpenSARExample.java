/*
 * Pyxis SAR Explorer: opens the example project that ships inside the program, so that
 * the SAR menu can be tried without any file at hand.
 *
 * The example holds COX-2 inhibitors from ChEMBL (CC BY-SA 3.0); its source is named in
 * the file's explanation and in pyxis/demo/MakeExample.java.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed
 * WITHOUT ANY WARRANTY; see the GNU General Public License for more details.
 */

package com.actelion.research.datawarrior.pyxis;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.task.AbstractTaskWithoutConfiguration;
import com.actelion.research.datawarrior.task.file.DETaskOpenFile;

import java.awt.Frame;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class DETaskOpenSARExample extends AbstractTaskWithoutConfiguration {
	public static final String TASK_NAME = "Open SAR Example";
	public static final String RESOURCE = "/html/pyxis/cox2-example.dwar";
	public static final String FILE_NAME = "COX-2 inhibitors (example).dwar";

	private final DataWarrior mApplication;
	private volatile DEFrame mNewFrame;

	public DETaskOpenSARExample(Frame parent, DataWarrior application) {
		super(parent, false);
		mApplication = application;
		}

	@Override
	public boolean isConfigurable() {
		return true;
		}

	@Override
	public String getTaskName() {
		return TASK_NAME;
		}

	/** @return the window the example was opened in, or null if it was not opened */
	public DEFrame getNewFrame() {
		return mNewFrame;
		}

	/**
	 * Copies the example out of the program into a fresh temporary folder. The copy is the
	 * user's to change; File &rarr; Save As keeps it.
	 */
	public static File extractExample() throws java.io.IOException {
		File directory = Files.createTempDirectory("pyxis-sar-example").toFile();
		directory.deleteOnExit();
		File file = new File(directory, FILE_NAME);
		file.deleteOnExit();
		try (InputStream in = DETaskOpenSARExample.class.getResourceAsStream(RESOURCE)) {
			if (in == null)
				throw new java.io.FileNotFoundException("The example project is missing from this installation.");
			Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		return file;
		}

	@Override
	public void runTask(Properties configuration) {
		try {
			File file = extractExample();
			mNewFrame = new DETaskOpenFile(mApplication).openFile(file, new Properties());
			System.out.println("Pyxis SAR example opened: "+file.length()+" bytes");
			}
		catch (java.io.IOException e) {
			showErrorMessage("The example project could not be opened: "+e.getMessage());
			}
		}
	}
