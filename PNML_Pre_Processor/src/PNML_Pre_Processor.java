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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.xerces.parsers.DOMParser;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import ch.qos.logback.classic.LoggerContext;
import fr.lip6.move.pnml.framework.utils.PNMLUtils;
import fr.lip6.move.pnml.framework.utils.exception.ImportException;
import fr.lip6.move.pnml.framework.utils.exception.InvalidIDException;
import fr.lip6.move.pnml.ptnet.hlapi.ArcHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.PageHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.PetriNetDocHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.PetriNetHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.PlaceHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.ToolInfoHLAPI;
import fr.lip6.move.pnml.ptnet.hlapi.TransitionHLAPI;
import jmt.gui.common.CommonConstants;
import jmt.gui.common.Defaults;
import jmt.gui.common.definitions.CommonModel;
import jmt.gui.common.definitions.SimulationDefinition;
import jmt.gui.common.distributions.Exponential;
import jmt.gui.common.forkStrategies.OutPath;
import jmt.gui.common.forkStrategies.ProbabilitiesFork;
import jmt.gui.common.serviceStrategies.ZeroStrategy;
import jmt.gui.common.xml.XMLArchiver;

/**
 * PNML Pre-Processor
 * 
 * Receives as input a PNML file representing a GSPN or SWN and outputs
 * the model of the GSPN or SWN in a JSIMG file to be used on JMT
 * 
 * Author: Lulai Zhu
 * Mar/2017
 * 
 */
public class PNML_Pre_Processor {

	private static final String PNML_DICE_E_VALUE = "value";
	private static final String PNML_DICE_A_VALUE_GRAMMAR = "grammar";
	private static final String PNML_DICE_U_TSERV_INFINITE = "http://es.unizar.dsico/pnconstants/tserv/infinite";
	private static final String PNML_DICE_U_TKIND_EXPONENTIAL = "http://es.unizar.disco/pnconstants/tkind/exponential";
	private static final String PNML_DICE_U_TKIND_IMMEDIATE_PRIORITY = "http://es.unizar.disco/pnconstants/tkind/immediatepriority";
	private static final String PNML_DICE_U_TKIND_IMMEDIATE = "http://es.unizar.disco/pnconstants/tkind/immediate";
	private static final String PNML_DICE_U_AKIND_INHIBITOR = "http://es.unizar.dsico/pnconstants/akind/inhibitor";
	private static final String PNML_DICE_U_COLOR_COLORSET = "http://es.unizar.dsico/pnconstants/color/colorset";
	private static final String PNML_DICE_U_COLOR_COLOR = "http://es.unizar.dsico/pnconstants/color/color";

	private static final String CURRENT_PATH = System.getProperty("user.dir") + File.separator;
	private static final String TEMPLATES_PATH = CURRENT_PATH + "templates" + File.separator;

	private static CommonModel model = new CommonModel();
	private static DOMParser parser = new DOMParser();

	private static boolean isNormalArc;

	private static class ColorSet {

		public String name;
		public boolean isOrdered;
		public List<Color> colors;

		public ColorSet(String name, boolean isOrdered, List<Color> colors) {
			this.name = name;
			this.isOrdered = isOrdered;
			this.colors = colors;
		}

	}

	private static class Color {

		public int id;
		public String name;
		public int numOfTokens;

		public Color(int id, String name, int numOfTokens) {
			this.id = id;
			this.name = name;
			this.numOfTokens = numOfTokens;
		}

	}

	public static void main(String argv[]) {
		if (argv.length < 3) {
			help();
		}

		try {
			File sourceFile = new File(argv[1]);
			File targetFile = new File(argv[2]);
			File indexFile = (argv.length >= 4) ? new File(argv[3]) : null;
			if (argv[0].equals("gspn")) {
				readGSPN(sourceFile, indexFile);
			} else if (argv[0].equals("swn-HadoopCap")) {
				readHadoopCap(sourceFile);
			} else {
				help();
			}
			writeModel(targetFile);
			((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
		} catch (Exception e) {
			((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void help() {
		System.err.println("Usage 1: PNML_Pre_Processor gspn [pnmlfilename] [jsimgfilename] [idxfilename]");
		System.err.println("Usage 2: PNML_Pre_Processor swn-HadoopCap [pnmlfilename] [jsimgfilename]");
		System.exit(0);
	}

	private static void readGSPN(File file, File index) throws ImportException, InvalidIDException, SAXException, IOException {
		PetriNetDocHLAPI doc = (PetriNetDocHLAPI) PNMLUtils.importPnmlDocument(file, false);
		PetriNetHLAPI net = doc.getNetsHLAPI().get(0);
		PageHLAPI page = net.getPagesHLAPI().get(0);
		List<PlaceHLAPI> places = page.getObjects_PlaceHLAPI();
		List<TransitionHLAPI> transitions = page.getObjects_TransitionHLAPI();
		List<ArcHLAPI> arcs = page.getObjects_ArcHLAPI();

		Object classKey = model.addClass("Token", CommonConstants.CLASS_TYPE_CLOSED, Integer.valueOf(0),
				Integer.valueOf(0), null);
		int population = 0;
		for (PlaceHLAPI p : places) {
			population += (p.getInitialMarking() != null) ? p.getInitialMarking().getText().intValue() : 0;
		}
		model.setClassPopulation(classKey, Integer.valueOf(population));

		Map<String, Object> nodeMap = new HashMap<String, Object>();
		for (PlaceHLAPI p : places) {
			String id = p.getId();
			Object key = model.addStation(id, CommonConstants.STATION_TYPE_PLACE);
			nodeMap.put(id, key);

			int marking = (p.getInitialMarking() != null) ? p.getInitialMarking().getText().intValue() : 0;
			model.setPreloadedJobs(key, classKey, Integer.valueOf(marking));

			List<ToolInfoHLAPI> specifics = p.getToolspecificsHLAPI();
			for (ToolInfoHLAPI s : specifics) {
				Element elem = createSpecificRootElement(s);
				parseSpecificPlaceParameters(elem, model, key);
			}
		}

		for (TransitionHLAPI t : transitions) {
			String id = t.getId();
			Object key = model.addStation(id, CommonConstants.STATION_TYPE_TRANSITION);
			nodeMap.put(id, key);

			model.setNumberOfServers(key, 0, Integer.valueOf(1));
			model.setFiringTimeDistribution(key, 0, new ZeroStrategy());
			model.setFiringPriority(key, 0, Integer.valueOf(0));
			model.setFiringWeight(key, 0, Double.valueOf(1.0));

			List<ToolInfoHLAPI> specifics = t.getToolspecificsHLAPI();
			for (ToolInfoHLAPI s : specifics) {
				Element elem = createSpecificRootElement(s);
				parseSpecificTransitionParameters(elem, model, key);
			}
		}

		Object nodeKey = null;
		Vector<Object> placeKeys = model.getStationKeysPlace();
		Vector<Object> transitionKeys = model.getStationKeysTransition();
		if (nodeKey == null && !placeKeys.isEmpty()) {
			nodeKey = placeKeys.get(0);
		}
		if (nodeKey == null && !transitionKeys.isEmpty()) {
			nodeKey = transitionKeys.get(0);
		}
		model.setClassRefStation(classKey, nodeKey);

		for (ArcHLAPI a : arcs) {
			Object sourceKey = nodeMap.get(a.getSourceHLAPI().getId());
			Object targetKey = nodeMap.get(a.getTargetHLAPI().getId());
			if (sourceKey != null && targetKey != null) {
				model.setConnected(sourceKey, targetKey, true);

				isNormalArc = true;
				List<ToolInfoHLAPI> specifics = a.getToolspecificsHLAPI();
				for (ToolInfoHLAPI s : specifics) {
					Element elem = createSpecificRootElement(s);
					parseSpecificArcParameters(elem);
				}

				int inscription = (a.getInscriptionHLAPI() != null) ? a.getInscriptionHLAPI().getText().intValue() : 1;
				if (CommonConstants.STATION_TYPE_TRANSITION.equals(model.getStationType(targetKey))) {
					if (isNormalArc) {
						model.setEnablingCondition(targetKey, 0, sourceKey, classKey, Integer.valueOf(inscription));
					} else {
						model.setInhibitingCondition(targetKey, 0, sourceKey, classKey, Integer.valueOf(inscription));
					}
				} else {
					model.setFiringOutcome(sourceKey, 0, targetKey, classKey, Integer.valueOf(inscription));
				}
			}
		}

		if (index != null) {
			BufferedReader reader = new BufferedReader(new FileReader(index));
			String id = reader.readLine();
			while (id != null) {
				nodeKey = nodeMap.get(id);
				if (placeKeys.contains(nodeKey)) {
					model.addMeasure(SimulationDefinition.MEASURE_QL, nodeKey, classKey);
				}
				if (transitionKeys.contains(nodeKey)) {
					String modeName = model.getTransitionModeName(nodeKey, 0);
					model.addMeasure(SimulationDefinition.MEASURE_FX, nodeKey, modeName);
				}
				id = reader.readLine();
			}
		}
		if (model.getMeasureKeys().isEmpty()) {
			for (Object pk : placeKeys) {
				model.addMeasure(SimulationDefinition.MEASURE_QL, pk, classKey);
			}
			for (Object tk : transitionKeys) {
				String modeName = model.getTransitionModeName(tk, 0);
				model.addMeasure(SimulationDefinition.MEASURE_FX, tk, modeName);
			}
		}
	}

	private static void readHadoopCap(File file) throws ImportException, InvalidIDException, SAXException, IOException {
		File template = new File(TEMPLATES_PATH + "HadoopCap.jsimg");
		XMLArchiver.loadModel(template, model);

		PetriNetDocHLAPI doc = (PetriNetDocHLAPI) PNMLUtils.importPnmlDocument(file, false);
		PetriNetHLAPI net = doc.getNetsHLAPI().get(0);
		PageHLAPI page = net.getPagesHLAPI().get(0);
		List<TransitionHLAPI> transitions = page.getObjects_TransitionHLAPI();

		List<ToolInfoHLAPI> specifics = net.getToolspecificsHLAPI();
		ArrayList<ColorSet> colorSets = new ArrayList<ColorSet>();
		for (ToolInfoHLAPI s : specifics) {
			Element elem = createSpecificRootElement(s);
			Object temp = parseSpecificColorSetDefinition(elem);
			if (temp != null) {
				colorSets.add((ColorSet) temp);
			} else {
				elem = createSpecificRootElement(s);
				temp = parseSpecificColorDefinition(elem);
				if (temp != null) {
					colorSets.get(colorSets.size() - 1).colors.add((Color) temp);
				}
			}
		}
		int degree = colorSets.size() - 4;

		Vector<Object> stationKeys = model.getStationKeys();
		Map<String, Object> stationMap = new HashMap<String, Object>();
		for (Object sk : stationKeys) {
			stationMap.put(model.getStationName(sk), sk);
		}
		for (int i = 0; i < degree; i++) {
			Object mapExecKey = model.addStation("MapExec" + i, CommonConstants.STATION_TYPE_DELAY);
			stationMap.put(model.getStationName(mapExecKey), mapExecKey);
			Object mapDoneKey = model.addStation("MapDone" + i, CommonConstants.STATION_TYPE_PLACE);
			stationMap.put(model.getStationName(mapDoneKey), mapDoneKey);
			model.setConnected(stationMap.get("MapAcqRes"), mapExecKey, true);
			model.setConnected(mapExecKey, mapDoneKey, true);
			model.setConnected(mapDoneKey, stationMap.get("MapRelRes"), true);

			Object redExecKey = model.addStation("RedExec" + i, CommonConstants.STATION_TYPE_DELAY);
			stationMap.put(model.getStationName(redExecKey), redExecKey);
			Object redDoneKey = model.addStation("RedDone" + i, CommonConstants.STATION_TYPE_PLACE);
			stationMap.put(model.getStationName(redDoneKey), redDoneKey);
			model.setConnected(stationMap.get("RedAcqRes"), redExecKey, true);
			model.setConnected(redExecKey, redDoneKey, true);
			model.setConnected(redDoneKey, stationMap.get("RedRelRes"), true);
		}

		ColorSet startSet = colorSets.get(degree);
		ColorSet reducingSet = colorSets.get(degree + 1);
		ColorSet mappingSet = colorSets.get(degree + 2);
		ColorSet resourceSet = colorSets.get(degree + 3);
		Map<String, Object> classMap = new HashMap<String, Object>();
		for (int i = 0; i < degree; i++) {
			Object jobKey = model.addClass("Job" + i, CommonConstants.CLASS_TYPE_CLOSED, Integer.valueOf(0),
					Integer.valueOf(startSet.colors.get(i).numOfTokens), null);
			classMap.put(model.getClassName(jobKey), jobKey);
			model.setClassRefStation(jobKey, stationMap.get("Think"));
			model.setPreloadedJobs(stationMap.get("Think"), jobKey, model.getClassPopulation(jobKey));

			Object resKey = model.addClass("Res" + i, CommonConstants.CLASS_TYPE_CLOSED, Integer.valueOf(0),
					Integer.valueOf(resourceSet.colors.get(i).numOfTokens), null);
			classMap.put(model.getClassName(resKey), resKey);
			model.setClassRefStation(resKey, stationMap.get("FreeRess"));
			model.setPreloadedJobs(stationMap.get("FreeRess"), resKey, model.getClassPopulation(resKey));

			Object flagKey = model.addClass("Flag" + i, CommonConstants.CLASS_TYPE_CLOSED, Integer.valueOf(0),
					Integer.valueOf(1), null);
			classMap.put(model.getClassName(flagKey), flagKey);
			model.setClassRefStation(flagKey, stationMap.get("ReadyForJob"));
			model.setPreloadedJobs(stationMap.get("ReadyForJob"), flagKey, model.getClassPopulation(flagKey));
		}

		for (int i = 0; i < degree; i++) {
			TransitionHLAPI start = getTransitionHLAPIByName(transitions,
					startSet.name + "_trans_" + startSet.name + "_" + i);
			specifics = start.getToolspecificsHLAPI();
			for (ToolInfoHLAPI s : specifics) {
				Element elem = createSpecificRootElement(s);
				Object distribution = parseSpecificFiringTimeDistribution(elem);
				if (distribution != null) {
					model.setServiceTimeDistribution(stationMap.get("Think"), classMap.get("Job" + i),
							distribution);
					break;
				}
			}

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("StartJob"), 0);
			}
			model.addTransitionMode(stationMap.get("StartJob"), Defaults.get("transitionModeName") + i);
			model.setNumberOfServers(stationMap.get("StartJob"), i, Integer.valueOf(1));
			model.setFiringTimeDistribution(stationMap.get("StartJob"), i, new ZeroStrategy());
			model.setFiringPriority(stationMap.get("StartJob"), i, Integer.valueOf(0));
			model.setFiringWeight(stationMap.get("StartJob"), i, Double.valueOf(1.0));
			model.setEnablingCondition(stationMap.get("StartJob"), i, stationMap.get("JobQueue"),
					classMap.get("Job" + i), Integer.valueOf(1));
			model.setEnablingCondition(stationMap.get("StartJob"), i, stationMap.get("ReadyForJob"),
					classMap.get("Flag" + i), Integer.valueOf(1));
			model.setInhibitingCondition(stationMap.get("StartJob"), i, stationMap.get("RedQueue"),
					classMap.get("Job" + i), Integer.valueOf(1));
			model.setFiringOutcome(stationMap.get("StartJob"), i, stationMap.get("ForkMaps"),
					classMap.get("Job" + i), Integer.valueOf(1));

			Object mapFork = model.getForkStrategy(stationMap.get("ForkMaps"), classMap.get("Job" + i));
			OutPath mapPath = new OutPath();
			mapPath.setProbability(Double.valueOf(1.0));
			mapPath.putEntry(Integer.valueOf(mappingSet.colors.get(i).numOfTokens), Double.valueOf(1.0));
			((ProbabilitiesFork) mapFork).getOutDetails().put(stationMap.get("MapQueue"), mapPath);

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("MapAcqRes"), 0);
			}
			for (int j = 0; j < degree; j++) {
				int index = i * degree + j;
				model.addTransitionMode(stationMap.get("MapAcqRes"), Defaults.get("transitionModeName") + i + j);
				model.setNumberOfServers(stationMap.get("MapAcqRes"), index, Integer.valueOf(1));
				model.setFiringTimeDistribution(stationMap.get("MapAcqRes"), index, new ZeroStrategy());
				model.setFiringPriority(stationMap.get("MapAcqRes"), index, Integer.valueOf(i == j ? 1 : 0));
				model.setFiringWeight(stationMap.get("MapAcqRes"), index, Double.valueOf(1.0));
				model.setEnablingCondition(stationMap.get("MapAcqRes"), index, stationMap.get("MapQueue"),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setEnablingCondition(stationMap.get("MapAcqRes"), index, stationMap.get("FreeRess"),
						classMap.get("Res" + j), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("MapAcqRes"), index, stationMap.get("MapExec" + j),
						classMap.get("Job" + i), Integer.valueOf(1));
			}

			TransitionHLAPI mapping = getTransitionHLAPIByName(transitions,
					mappingSet.name + "_trans_" + mappingSet.name + "_" + i);
			specifics = mapping.getToolspecificsHLAPI();
			for (ToolInfoHLAPI s : specifics) {
				Element elem = createSpecificRootElement(s);
				Object distribution = parseSpecificFiringTimeDistribution(elem);
				if (distribution != null) {
					for (int j = 0; j < degree; j++) {
						model.setServiceTimeDistribution(stationMap.get("MapExec" + j), classMap.get("Job" + i),
								distribution);
					}
					break;
				}
			}

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("MapRelRes"), 0);
			}
			for (int j = 0; j < degree; j++) {
				int index = i * degree + j;
				model.addTransitionMode(stationMap.get("MapRelRes"), Defaults.get("transitionModeName") + i + j);
				model.setNumberOfServers(stationMap.get("MapRelRes"), index, Integer.valueOf(1));
				model.setFiringTimeDistribution(stationMap.get("MapRelRes"), index, new ZeroStrategy());
				model.setFiringPriority(stationMap.get("MapRelRes"), index, Integer.valueOf(0));
				model.setFiringWeight(stationMap.get("MapRelRes"), index, Double.valueOf(1.0));
				model.setEnablingCondition(stationMap.get("MapRelRes"), index, stationMap.get("MapDone" + j),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("MapRelRes"), index, stationMap.get("JoinMaps"),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("MapRelRes"), index, stationMap.get("FreeRess"),
						classMap.get("Res" + j), Integer.valueOf(1));
			}

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("RunRedPhase"), 0);
			}
			model.addTransitionMode(stationMap.get("RunRedPhase"), Defaults.get("transitionModeName") + i);
			model.setNumberOfServers(stationMap.get("RunRedPhase"), i, Integer.valueOf(1));
			model.setFiringTimeDistribution(stationMap.get("RunRedPhase"), i, new ZeroStrategy());
			model.setFiringPriority(stationMap.get("RunRedPhase"), i, Integer.valueOf(0));
			model.setFiringWeight(stationMap.get("RunRedPhase"), i, Double.valueOf(1.0));
			model.setEnablingCondition(stationMap.get("RunRedPhase"), i, stationMap.get("MapPhaseOver"),
					classMap.get("Job" + i), Integer.valueOf(1));
			model.setFiringOutcome(stationMap.get("RunRedPhase"), i, stationMap.get("ForkReds"),
					classMap.get("Job" + i), Integer.valueOf(1));
			model.setFiringOutcome(stationMap.get("RunRedPhase"), i, stationMap.get("ReadyForJob"),
					classMap.get("Flag" + i), Integer.valueOf(1));

			Object redFork = model.getForkStrategy(stationMap.get("ForkReds"), classMap.get("Job" + i));
			OutPath redPath = new OutPath();
			redPath.setProbability(Double.valueOf(1.0));
			redPath.putEntry(Integer.valueOf(reducingSet.colors.get(i).numOfTokens), Double.valueOf(1.0));
			((ProbabilitiesFork) redFork).getOutDetails().put(stationMap.get("RedQueue"), redPath);

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("RedAcqRes"), 0);
			}
			for (int j = 0; j < degree; j++) {
				int index = i * degree + j;
				model.addTransitionMode(stationMap.get("RedAcqRes"), Defaults.get("transitionModeName") + i + j);
				model.setNumberOfServers(stationMap.get("RedAcqRes"), index, Integer.valueOf(1));
				model.setFiringTimeDistribution(stationMap.get("RedAcqRes"), index, new ZeroStrategy());
				model.setFiringPriority(stationMap.get("RedAcqRes"), index, Integer.valueOf(i == j ? 1 : 0));
				model.setFiringWeight(stationMap.get("RedAcqRes"), index, Double.valueOf(1.0));
				model.setEnablingCondition(stationMap.get("RedAcqRes"), index, stationMap.get("RedQueue"),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setEnablingCondition(stationMap.get("RedAcqRes"), index, stationMap.get("FreeRess"),
						classMap.get("Res" + j), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("RedAcqRes"), index, stationMap.get("RedExec" + j),
						classMap.get("Job" + i), Integer.valueOf(1));
			}

			TransitionHLAPI reducing = getTransitionHLAPIByName(transitions,
					reducingSet.name + "_trans_" + reducingSet.name + "_" + i);
			specifics = reducing.getToolspecificsHLAPI();
			for (ToolInfoHLAPI s : specifics) {
				Element elem = createSpecificRootElement(s);
				Object distribution = parseSpecificFiringTimeDistribution(elem);
				if (distribution != null) {
					for (int j = 0; j < degree; j++) {
						model.setServiceTimeDistribution(stationMap.get("RedExec" + j), classMap.get("Job" + i),
								distribution);
					}
					break;
				}
			}

			if (i == 0) {
				model.deleteTransitionMode(stationMap.get("RedRelRes"), 0);
			}
			for (int j = 0; j < degree; j++) {
				int index = i * degree + j;
				model.addTransitionMode(stationMap.get("RedRelRes"), Defaults.get("transitionModeName") + i + j);
				model.setNumberOfServers(stationMap.get("RedRelRes"), index, Integer.valueOf(1));
				model.setFiringTimeDistribution(stationMap.get("RedRelRes"), index, new ZeroStrategy());
				model.setFiringPriority(stationMap.get("RedRelRes"), index, Integer.valueOf(0));
				model.setFiringWeight(stationMap.get("RedRelRes"), index, Double.valueOf(1.0));
				model.setEnablingCondition(stationMap.get("RedRelRes"), index, stationMap.get("RedDone" + j),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("RedRelRes"), index, stationMap.get("JoinReds"),
						classMap.get("Job" + i), Integer.valueOf(1));
				model.setFiringOutcome(stationMap.get("RedRelRes"), index, stationMap.get("FreeRess"),
						classMap.get("Res" + j), Integer.valueOf(1));
			}
		}

		for (int i = 0; i < degree; i++) {
			model.addMeasure(SimulationDefinition.MEASURE_X, stationMap.get("JoinReds"), classMap.get("Job" + i));
		}
	}

	private static void writeModel(File file) {
		XMLArchiver.saveModel(file, model);
	}

	private static Element createSpecificRootElement(ToolInfoHLAPI specific) throws SAXException, IOException {
		StringBuffer buffer = specific.getFormattedXMLBuffer();
		parser.parse(new InputSource(new StringReader(buffer.toString())));
		return parser.getDocument().getDocumentElement();
	}

	private static void parseSpecificPlaceParameters(Element elem, CommonModel model, Object place) {
		return;
	}

	private static void parseSpecificTransitionParameters(Element elem, CommonModel model, Object transition) {
		Element value = getFirstChildElementByTagName(elem, PNML_DICE_E_VALUE);
		String grammar = value.getAttribute(PNML_DICE_A_VALUE_GRAMMAR);
		if (PNML_DICE_U_TSERV_INFINITE.equals(grammar)) {
			model.setNumberOfServers(transition, 0, Integer.valueOf(-1));
		} else if (PNML_DICE_U_TKIND_EXPONENTIAL.equals(grammar)) {
			Exponential exp = new Exponential();
			exp.getParameter(0).setValue(Double.valueOf(value.getTextContent()));
			model.setFiringTimeDistribution(transition, 0, exp);
			model.setFiringPriority(transition, 0, Integer.valueOf(-1));
			model.setFiringWeight(transition, 0, Double.valueOf(1.0));
		} else if (PNML_DICE_U_TKIND_IMMEDIATE_PRIORITY.equals(grammar)) {
			model.setFiringPriority(transition, 0, Integer.valueOf(value.getTextContent()));
		} else if (PNML_DICE_U_TKIND_IMMEDIATE.equals(grammar)) {
			model.setFiringWeight(transition, 0, Double.valueOf(value.getTextContent()));
		}
	}

	private static void parseSpecificArcParameters(Element elem) {
		Element value = getFirstChildElementByTagName(elem, PNML_DICE_E_VALUE);
		String grammar = value.getAttribute(PNML_DICE_A_VALUE_GRAMMAR);
		if (PNML_DICE_U_AKIND_INHIBITOR.equals(grammar)) {
			isNormalArc = false;
		}
	}

	private static ColorSet parseSpecificColorSetDefinition(Element elem) {
		List<Element> values = getChildElementsByTagName(elem, PNML_DICE_E_VALUE);
		String grammar = values.get(0).getAttribute(PNML_DICE_A_VALUE_GRAMMAR);
		if (PNML_DICE_U_COLOR_COLORSET.equals(grammar)) {
			String name = values.get(0).getTextContent();
			boolean isOrdered = values.get(1).getTextContent().equals("1");
			return new ColorSet(name, isOrdered, new ArrayList<Color>());
		}
		return null;
	}

	private static Color parseSpecificColorDefinition(Element elem) {
		List<Element> values = getChildElementsByTagName(elem, PNML_DICE_E_VALUE);
		String grammar = values.get(0).getAttribute(PNML_DICE_A_VALUE_GRAMMAR);
		if (PNML_DICE_U_COLOR_COLOR.equals(grammar)) {
			int id = Integer.parseInt(values.get(0).getTextContent());
			String name = values.get(1).getTextContent();
			int numOfTokens = Integer.parseInt(values.get(2).getTextContent());
			return new Color(id, name, numOfTokens);
		}
		return null;
	}

	private static Object parseSpecificFiringTimeDistribution(Element elem) {
		Element value = getFirstChildElementByTagName(elem, PNML_DICE_E_VALUE);
		String grammar = value.getAttribute(PNML_DICE_A_VALUE_GRAMMAR);
		if (PNML_DICE_U_TKIND_EXPONENTIAL.equals(grammar)) {
			Exponential exp = new Exponential();
			exp.getParameter(0).setValue(Double.valueOf(value.getTextContent()));
			return exp;
		}
		return null;
	}

	private static PlaceHLAPI getPlaceHLAPIByName(List<PlaceHLAPI> places, String name) {
		for (PlaceHLAPI p : places) {
			if (p.getNameHLAPI() != null && name.equals(p.getNameHLAPI().getText())) {
				return p;
			}
		}
		return null;
	}

	private static TransitionHLAPI getTransitionHLAPIByName(List<TransitionHLAPI> transitions, String name) {
		for (TransitionHLAPI t : transitions) {
			if (t.getNameHLAPI() != null && name.equals(t.getNameHLAPI().getText())) {
				return t;
			}
		}
		return null;
	}

	private static Element getFirstChildElementByTagName(Element elem, String name) {
		NodeList childNodes = elem.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node cn = childNodes.item(i);
			if (cn.getNodeType() == Node.ELEMENT_NODE && cn.getNodeName().equals(name)) {
				return (Element) cn;
			}
		}
		return null;
	}

	private static List<Element> getChildElementsByTagName(Element elem, String name) {
		List<Element> childElems = new ArrayList<Element>();
		NodeList childNodes = elem.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node cn = childNodes.item(i);
			if (cn.getNodeType() == Node.ELEMENT_NODE && cn.getNodeName().equals(name)) {
				childElems.add((Element) cn);
			}
		}
		return childElems;
	}

}
