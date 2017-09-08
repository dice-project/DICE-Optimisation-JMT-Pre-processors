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

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import jmt.gui.common.CommonConstants;
import jmt.gui.common.definitions.CommonModel;
import jmt.gui.common.distributions.Exponential;
import jmt.gui.common.xml.XMLArchiver;

/**
 * DAG Pre-Processor
 * 
 * Receives as input a JSON file representing a DAG and outputs the model
 * of the DAG in a JSIMG file to be used on JMT
 * 
 * Author: Vitor S. Lopes
 * Aug/2016
 * 
 */
public class DAG_Pre_Processor {

	private static CommonModel model = new CommonModel();
	private static ArrayList<String> source = new ArrayList<String>();
	private static ArrayList<String> target = new ArrayList<String>();
	private static ArrayList<String> sequence = new ArrayList<String>();
	private static ArrayList<Integer> paralellism_level = new ArrayList<Integer>();
	private static ArrayList<Integer> input_degree = new ArrayList<Integer>();
	private static HashMap<String, ArrayList<Object>> queues = new  HashMap<String, ArrayList<Object>>();
	private static Object fork = new Object();
	private static Object join = new Object();
	private static HashMap<String, Object> scalers = new HashMap<String, Object>();
	private static Object src = new Object();
	private static Object sink = new Object();

	public static void main(String argv[]) {
		if (argv.length < 2) {
			help();
		}
		readDAG(new File(argv[0]));
		writeDAG(new File(argv[1]));
	}

	private static void help() {
		System.err.println("Usage: DAG_Pre_Processor [jsonfilename] [jsimgfilename]");
		System.exit(0);
	}

	private static void readDAG(File file) {
		try {
			FileReader reader = new FileReader(file);
			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(reader);

			JSONArray connections = (JSONArray) jsonObject.get("Connections");
			Iterator it = connections.iterator();
			while (it.hasNext()) {
				JSONObject connection = (JSONObject) it.next();
				source.add((String) connection.get("source"));
				target.add((String) connection.get("target"));
			}

			for (int i = 0; i < source.size(); i++) {
				String src = source.get(i);
				if (!target.contains(src)) {
					sequence.add(src);
					break;
				}
			}
			for (int i = 0; i < source.size(); i++) {
				int index = source.indexOf(sequence.get(i));
				sequence.add(target.get(index));
			}

			for (int i = 0; i < sequence.size(); i++) {
				JSONObject node = FindNodebyName(jsonObject, sequence.get(i));
				paralellism_level.add(Integer.valueOf((String) node.get("paralellism_level")));
				input_degree.add(Integer.valueOf((String) node.get("input_degree")));
			}

			generateQueues();
			generateForksandJoins();
			generateSourceAndSink();
			generateConnections();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void writeDAG(File file) {
		XMLArchiver.saveModel(file, model);
	}

	private static void generateQueues() {
		for (int i = 0; i < sequence.size(); i++) {
			ArrayList<Object> list = new ArrayList<Object>();
			for (int j = 0; j < paralellism_level.get(i).intValue(); j++) {
				list.add(model.addStation("Queue " + (i + 1) + "_" + (j + 1), CommonConstants.STATION_TYPE_SERVER));
			}
			queues.put(sequence.get(i), list);
		}
	}

	private static void generateForksandJoins() {
		fork = model.addStation("Fork 1", CommonConstants.STATION_TYPE_FORK);
		model.setStationNumberOfServers(fork, input_degree.get(0));
		join = model.addStation("Join 1", CommonConstants.STATION_TYPE_JOIN);
		for (int i = 1; i < sequence.size(); i++) {
			scalers.put(sequence.get(i), model.addStation("Scaler " + i, CommonConstants.STATION_TYPE_SCALER));
			model.setStationNumberOfServers(scalers.get(sequence.get(i)), input_degree.get(i));
		}
	}

	private static void generateSourceAndSink() {
		Exponential exp = new Exponential();
		Object cls = model.addClass("Class1", CommonConstants.CLASS_TYPE_OPEN, 0, 0, exp);
		src = model.addStation("Source 1", CommonConstants.STATION_TYPE_SOURCE);
		sink = model.addStation("Sink 1", CommonConstants.STATION_TYPE_SINK);
		model.setClassRefStation(cls, src);
	}

	private static void generateConnections() {
		model.setConnected(src, fork, true);
		for (int i = 0; i < sequence.size(); i++) {
			ArrayList<Object> list = queues.get(sequence.get(i));
			if (i == 0) {
				for (int j = 0; j < list.size(); j++) {
					model.setConnected(fork, list.get(j), true);
					model.setConnected(list.get(j), scalers.get(sequence.get(i + 1)), true);
				}
			} else if (i == sequence.size() - 1) {
				for (int j = 0; j < list.size(); j++) {
					model.setConnected(scalers.get(sequence.get(i)), list.get(j), true);
					model.setConnected(list.get(j), join, true);
				}
			} else {
				for (int j = 0; j < list.size(); j++) {
					model.setConnected(scalers.get(sequence.get(i)), list.get(j), true);
					model.setConnected(list.get(j), scalers.get(sequence.get(i + 1)), true);
				}
			}
		}
		model.setConnected(join, sink, true);
	}

	private static JSONObject FindNodebyName(JSONObject jsonObject, String name) {
		JSONArray nodes = (JSONArray) jsonObject.get("Nodes");
		Iterator it = nodes.iterator();
		while (it.hasNext()) {
			JSONObject node = (JSONObject) it.next();
			if (node.get("name").equals(name)) {
				return node;
			}
		}
		return null;
	}

}
