/**
 * Copyright (C) 2017, DICE H2020 WP3 Team

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;

import jmt.gui.common.definitions.CommonModel;
import jmt.gui.common.CommonConstants;
import jmt.gui.common.xml.XMLArchiver;
import jmt.gui.common.xml.XMLReader;

/**
 * MRT Pre-Processor
 * 
 * Receives as input an XML file containing MRTs and outputs the model
 * with the MRTs in a JSIMG file to be used on JMT
 * 
 * Author: Vitor S. Lopes
 * Aug/2016
 * 
 */
public class MRT_Pre_Processor {

	private static CommonModel model = new CommonModel();
	private static ArrayList<String> name = new ArrayList<String>();
	private static ArrayList<String> input = new ArrayList<String>();
	private static ArrayList<String> output = new ArrayList<String>();
	private static int[][] forkdegree;
	private static int[][] threshold;
	private static int[] mapper;
	private static int[] reducer;

	public static void main(String argv[]) {
		if (argv.length < 2) {
			help();
		}
		readMRT(new File(argv[0]));
		writeMRT(new File(argv[1]));
	}

	private static void help() {
		System.err.println("Usage: MRT_Pre_Processor [xmlfilename] [jsimgfilename]");
		System.exit(0);
	}

	private static void readMRT(File file) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			Element sim = (Element) doc.getElementsByTagName("sim").item(0);
			if (sim != null) {
				XMLReader.parseXML(sim, model);
			}

			NodeList node_mr = doc.getElementsByTagName("template_mapreduce");
			forkdegree = new int[node_mr.getLength()][2];
			threshold = new int[node_mr.getLength()][model.getClassKeys().size()];
			mapper = new int[node_mr.getLength()];
			reducer = new int[node_mr.getLength()];
			for (int i = 0; i < node_mr.getLength(); i++) {
				Element mapreduce = (Element) node_mr.item(i);
				if (mapreduce.hasAttribute("name")) {
					name.add(i, mapreduce.getAttribute("name"));
				} else{
					name.add(i, null);
				}

				Element in = (Element) mapreduce.getElementsByTagName("input").item(0);
				if (in.hasAttribute("name")) {
					input.add(i, in.getAttribute("name"));
				} else{
					input.add(i, null);
				}

				Element fork = (Element) mapreduce.getElementsByTagName("fork").item(0);
				forkdegree[i][0] = Integer.parseInt(fork.getElementsByTagName("map").item(0).getTextContent());
				forkdegree[i][1] = Integer.parseInt(fork.getElementsByTagName("red").item(0).getTextContent());

				mapper[i] = Integer.parseInt(mapreduce.getElementsByTagName("mapper").item(0).getTextContent());
				reducer[i] = Integer.parseInt(mapreduce.getElementsByTagName("reducer").item(0).getTextContent());

				Element semaphore = (Element) mapreduce.getElementsByTagName("semaphore").item(0);
				NodeList classes = (NodeList) semaphore.getElementsByTagName("class");
				for (int j = 0; j < classes.getLength(); j++) {
					Element thisClass = (Element) classes.item(j);
					if (thisClass.hasAttribute("name")) {
						String className = thisClass.getAttribute("name");
						Object classKey = model.getClassByName(className);
						if (classKey != null) {
							int index = model.getClassKeys().indexOf(classKey);
							threshold[i][index] = Integer.parseInt(thisClass.getTextContent());
						}
					}
				}

				Element out = (Element) mapreduce.getElementsByTagName("output").item(0);
				if (out.hasAttribute("name")) {
					output.add(i, out.getAttribute("name"));
				} else {
					output.add(i, null);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void writeMRT(File file) {
		Object[][] forks = new Object[name.size()][2];
		Object[] semaphores = new Object[name.size()];
		Object[][] joins = new Object[name.size()][2];
		for (int i = 0; i < name.size(); i++) {
			Object[] queues = new Object[mapper[i] + reducer[i]];

			forks[i][0] = model.addStation("Fork " + (i + 1) + "_1", CommonConstants.STATION_TYPE_FORK);
			forks[i][1] = model.addStation("Fork " + (i + 1) + "_2", CommonConstants.STATION_TYPE_FORK);
			for (int j = 0; j < mapper[i] + reducer[i]; j++) {
				queues[j] = model.addStation("Queue " + (i + 1) + "_" + (j + 1), CommonConstants.STATION_TYPE_SERVER);
			}
			semaphores[i] = model.addStation("Semaphore " + (i + 1), CommonConstants.STATION_TYPE_SEMAPHORE);
			joins[i][0] = model.addStation("Join " + (i + 1) + "_1", CommonConstants.STATION_TYPE_JOIN);
			joins[i][1] = model.addStation("Join " + (i + 1) + "_2", CommonConstants.STATION_TYPE_JOIN);

			for (int j = 0; j < mapper[i] + reducer[i]; j++) {
				if (j < mapper[i]) {
					model.setConnected(forks[i][0], queues[j], true);
					model.setConnected(queues[j], semaphores[i], true);
				} else {
					model.setConnected(forks[i][1], queues[j], true);
					model.setConnected(queues[j], joins[i][1], true);
				}
			}
			model.setConnected(semaphores[i], joins[i][0], true);
			model.setConnected(joins[i][0], forks[i][1], true);

			model.setStationNumberOfServers(forks[i][0], Integer.valueOf(forkdegree[i][0]));
			model.setStationNumberOfServers(forks[i][1], Integer.valueOf(forkdegree[i][1]));
			for (int j = 0; j < model.getClassKeys().size(); j++) {
				if (threshold[i][j] > 0) {
					model.setSemaphoreThreshold(semaphores[i], model.getClassKeys().get(j), Integer.valueOf(threshold[i][j]));
				}
			}
		}

		for (int i = 0; i < name.size(); i++) {
			Object in = null;
			Object out = null;

			if (input.get(i) != null) {
				if (model.getStationByName(input.get(i)) != null) {
					in = model.getStationByName(input.get(i));
				} else if (name.contains(input.get(i))) {
					in = joins[name.indexOf(input.get(i))][1];
				}
			}
			if (output.get(i) != null) {
				if (model.getStationByName(output.get(i)) != null) {
					out = model.getStationByName(output.get(i));
				} else if (name.contains(output.get(i))) {
					out = forks[name.indexOf(output.get(i))][0];
				}
			}

			model.setConnected(in, forks[i][0], true);
			model.setConnected(joins[i][1], out, true);
		}

		XMLArchiver.saveModel(file, model);
	}

}
