import se.sics.cooja.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

import java.io.FileOutputStream;
import java.util.zip.GZIPOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;

import se.sics.cooja.VisPlugin.PluginRequiresVisualizationException;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;

import java.util.regex.*;
import java.nio.file.*;


public class GenTestcsc {
  public static final long MICROSECOND = 1L;
  public static final long MILLISECOND = 1000*MICROSECOND;

  /*private static long EVENT_COUNTER = 0;*/

  private Vector<Mote> motes = new Vector<Mote>();
  private Vector<Mote> motesUninit = new Vector<Mote>();
  
  private Vector<MoteType> moteTypes = new Vector<MoteType>();

  /* If true, run simulation at full speed */
  private boolean speedLimitNone = true;
  /* Limit simulation speed to maxSpeed; if maxSpeed is 1.0 simulation is run at real-time speed */
  private double speedLimit;
  /* Used to restrict simulation speed */
  private long speedLimitLastSimtime;
  private long speedLimitLastRealtime;

  private long currentSimulationTime = 0;

  private String title = null;

  private RadioMedium currentRadioMedium = null;

  private boolean isRunning = false;

  private boolean stopSimulation = false;

  private Thread simulationThread = null;

  private static GUI myGUI = null;

  private long randomSeed = 123456;

  private boolean randomSeedGenerated = false;

  private long maxMoteStartupDelay = 1000*MILLISECOND;

  private Random randomGenerator = new Random();

  private boolean hasMillisecondObservers = false;

  private int logOutputBufferSize;

  /* Event queue */
  private EventQueue eventQueue = new EventQueue();

  /* Poll requests */
  private boolean hasPollRequests = false;
  private ArrayDeque<Runnable> pollRequests = new ArrayDeque<Runnable>();


  private Class<? extends Mote> moteClass = null;

  public File currentConfigFile = null;

  private ArrayList<COOJAProject> currentProjects = new ArrayList<COOJAProject>();

  private Vector<Plugin> startedPlugins = new Vector<Plugin>();

  public static Simulation mySimulation = null;

 public static ArrayList<Element> config = new ArrayList<Element>();

 private JDesktopPane myDesktopPane;

/*
  private SimEventCentral eventCentral = new SimEventCentral(this);
  public SimEventCentral getEventCentral() {
    return eventCentral;
  }
*/
/**
   * Returns all mote types in simulation.
   *
   * @return All mote types
   */
  public MoteType[] getMoteTypes() {
    MoteType[] types = new MoteType[moteTypes.size()];
    moteTypes.toArray(types);
    return types;
  }

/**
   * Returns mote type with given identifier.
   *
   * @param identifier
   *          Mote type identifier
   * @return Mote type or null if not found
   */
  public MoteType getMoteType(String identifier) {
    for (MoteType moteType : getMoteTypes()) {
      if (moteType.getIdentifier().equals(identifier)) {
        return moteType;
      }
    }
    return null;
  }
 /**
   * Returns simulation with with given ID.
   *
   * @param id ID
   * @return Mote or null
   * @see Mote#getID()
   */
  public Mote getMoteWithID(int id) {
    for (Mote m: motes) {
      if (m.getID() == id) {
        return m;
      }
    }
    return null;
  }

  public GenTestcsc()
  {
    JDesktopPane desktop = new JDesktopPane();
    myDesktopPane = desktop;
    myGUI = new GUI(desktop);
    mySimulation = new Simulation(myGUI);
  }
    
/**
   * Returns number of motes in this simulation.
   *
   * @return Number of motes
   */
  public int getMotesCount() {
    return motes.size();
  }


 /**
   * Adds given mote type to simulation.
   *
   * @param newMoteType Mote type
   */
  public void addMoteType(MoteType newMoteType) {
    moteTypes.add(newMoteType);

  }

public Mote getMote(int pos) {
    return motes.get(pos);
  }

public void addMote(final Mote mote) {
motes.add(mote);
}

/**
   * @return Max simulation speed ratio. Returns null if no limit.
   */
  public Double getSpeedLimit() {
    if (speedLimitNone) {
      return null;
    }
    return new Double(speedLimit);
  }

 /**
   * @return Random seed (converted to a string)
   */
  public String getRandomSeedString() {
    return Long.toString(randomSeed);
  }

 /**
   * @return Random seed
   */
  public long getRandomSeed() {
    return randomSeed;
  }

   /* Returns the current simulation config represented by XML elements. This
   * config also includes the current radio medium, all mote types and motes.
   *
   * @return Current simulation config
   */
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<Element>();

    Element element;

    // Title
    element = new Element("title");
    element.setText(title);
    config.add(element);

    /* Max simulation speed */
    if (!speedLimitNone) {
      element = new Element("speedlimit");
      element.setText("" + getSpeedLimit());
      config.add(element);
    }

    // Random seed
    element = new Element("randomseed");
    if (randomSeedGenerated) {
      element.setText("generated");
    } else {
      element.setText(Long.toString(getRandomSeed()));
    }
    config.add(element);

    // Max mote startup delay
    element = new Element("motedelay_us");
    element.setText(Long.toString(maxMoteStartupDelay));
    config.add(element);

    // Radio Medium
    element = new Element("radiomedium");
    element.setText(currentRadioMedium.getClass().getName());

    Collection<Element> radioMediumXML = currentRadioMedium.getConfigXML();
    if (radioMediumXML != null) {
      element.addContent(radioMediumXML);
    }
    config.add(element);

    /* Event central */

/*
    element = new Element("events");
    element.addContent(eventCentral.getConfigXML());
    config.add(element);
*/

    // Mote types
    for (MoteType moteType : getMoteTypes()) {
      element = new Element("motetype");
      element.setText(moteType.getClass().getName());

      Collection<Element> moteTypeXML = moteType.getConfigXML(mySimulation);
      if (moteTypeXML != null) {
        element.addContent(moteTypeXML);
      }
      config.add(element);
    }

    // Motes
    for (Mote mote : motes) {
      element = new Element("mote");

      Collection<Element> moteConfig = mote.getConfigXML();
      if (moteConfig == null) {
        moteConfig = new ArrayList<Element>();
      }

      /* Add mote type identifier */
      Element typeIdentifier = new Element("motetype_identifier");
      typeIdentifier.setText(mote.getType().getIdentifier());
      moteConfig.add(typeIdentifier);

      element.addContent(moteConfig);
      config.add(element);
    }

    return config;
  }


  /**
   * @return Current desktop pane (simulator visualizer)
   */
  public JDesktopPane getDesktopPane() {
    return myDesktopPane;
  }

  /**
   * Sets the current simulation config depending on the given configuration.
   *
   * @param configXML Simulation configuration
   * @param visAvailable True if simulation is allowed to show visualizers
   * @param manualRandomSeed Simulation random seed. May be null, in which case the configuration is used
   * @return True if simulation was configured successfully
   * @throws Exception If configuration could not be loaded
   */
  public boolean setConfigXML(Collection<Element> configXML,
      boolean visAvailable, Long manualRandomSeed) throws Exception {

    // Parse elements
    for (Element element : configXML) {

      // Title
      if (element.getName().equals("title")) {
        title = element.getText();
      }

      /* Max simulation speed */
/*
      if (element.getName().equals("speedlimit")) {
        String text = element.getText();
        if (text.equals("null")) {
          setSpeedLimit(null);
        } else {
          setSpeedLimit(Double.parseDouble(text));
        }
      }
*/
      // Random seed
      if (element.getName().equals("randomseed")) {
        long newSeed;

        if (element.getText().equals("generated")) {
          randomSeedGenerated = true;
          newSeed = new Random().nextLong();
        } else {
          newSeed = Long.parseLong(element.getText());
        }
        if (manualRandomSeed != null) {
          newSeed = manualRandomSeed;
        }

        mySimulation.setRandomSeed(newSeed);
      }
      // Max mote startup delay
      if (element.getName().equals("motedelay")) {
        maxMoteStartupDelay = Integer.parseInt(element.getText())*MILLISECOND;
      }
      if (element.getName().equals("motedelay_us")) {
        maxMoteStartupDelay = Integer.parseInt(element.getText());
      }

      // Radio medium
      if (element.getName().equals("radiomedium")) {
        String radioMediumClassName = element.getText().trim();
        Class<? extends RadioMedium> radioMediumClass = myGUI.tryLoadClass(
            mySimulation, RadioMedium.class, radioMediumClassName);

        if (radioMediumClass != null) {
          // Create radio medium specified in config
          try {
            currentRadioMedium = RadioMedium.generateRadioMedium(radioMediumClass, mySimulation);
          } catch (Exception e) {
            currentRadioMedium = null;
          }
        }

        // Show configure simulation dialog
/*
        boolean createdOK = false;
        if (visAvailable) {
          createdOK = CreateSimDialog.showDialog(GUI.getTopParentContainer(), this);
        } else {
          createdOK = true;
        }

        if (!createdOK) {
          throw new Exception("Load aborted by user");
        }
*/

        // Check if radio medium specific config should be applied
/*
        if (radioMediumClassName.equals(currentRadioMedium.getClass().getName())) {
          currentRadioMedium.setConfigXML(element.getChildren(), visAvailable);
        } else {
        }
*/
      }

      /* Event central */
      if (element.getName().equals("events")) {
        // eventCentral.setConfigXML(this, element.getChildren(), visAvailable);
        logOutputBufferSize = Integer.parseInt(element.getText());

      }

      // Mote type
      if (element.getName().equals("motetype")) {
        String moteTypeClassName = element.getText().trim();
        System.out.println("Debug: moteTypeClassName"+moteTypeClassName);

        /* Try to recreate simulation using a different mote type */

        Class<? extends MoteType> moteTypeClass = myGUI.tryLoadClass(mySimulation,
            MoteType.class, moteTypeClassName);

        if (moteTypeClass == null) {
         throw new MoteType.MoteTypeCreationException("Could not load mote type class: " + moteTypeClassName);
        }

        MoteType moteType = moteTypeClass.getConstructor((Class[]) null).newInstance();

        boolean createdOK = moteType.setConfigXML(mySimulation, element.getChildren(),
            visAvailable);
        if (createdOK) {
          addMoteType(moteType);
        } else {
          throw new Exception("All mote types were not recreated");
        }
      }

      /* Mote */
      if (element.getName().equals("mote")) {

        /* Read mote type identifier */
        MoteType moteType = null;
        for (Element subElement: (Collection<Element>) element.getChildren()) {
          if (subElement.getName().equals("motetype_identifier")) {
            moteType = getMoteType(subElement.getText());
            if (moteType == null) {
              throw new Exception("No mote type '" + subElement.getText() + "' for mote");
            }
            break;
          }
        }
        if (moteType == null) {
          throw new Exception("No mote type specified for mote");
        }

        /* Create mote using mote type */

        Mote mote = moteType.generateMote(mySimulation);
        if (mote.setConfigXML(mySimulation, element.getChildren(), visAvailable)) {
        	if (getMoteWithID(mote.getID()) != null) {
        	} else {
        		addMote(mote);
        	}
        } else {
          throw new Exception("All motes were not recreated");
        }
      }
  }
/*
    if (currentRadioMedium != null) {
      currentRadioMedium.simulationFinishedLoading();
    }
*/
    return true;
}
   public void saveSimulationConfig(File file) {
    this.currentConfigFile = file; /* Used to generate config relative paths */
    try {
      this.currentConfigFile = this.currentConfigFile.getCanonicalFile();
    } catch (IOException e) {
    }

    try {
      // Create and write to document
      Document doc = new Document(extractSimulationConfig());
      OutputStream out = new FileOutputStream(file);

      if (file.getName().endsWith(".gz")) {
        out = new GZIPOutputStream(out);
      }

      XMLOutputter outputter = new XMLOutputter();
      outputter.setFormat(Format.getPrettyFormat());
      outputter.output(doc, out);
      out.close();
      System.out.println("Saved to file: " + file.getAbsolutePath());

    } catch (Exception e) {
      e.printStackTrace();
   }
 }
 public Element extractSimulationConfig() {
    // Create simulation config
    Element root = new Element("simconf");

    System.out.println("currentProjects value "+currentProjects.toString());

    /* Store extension directories meta data */
    for (COOJAProject project: currentProjects) {
      Element projectElement = new Element("project");
      projectElement.addContent((project.dir).getPath().replaceAll("\\\\", "/"));
      projectElement.setAttribute("EXPORT", "discard");
      root.addContent(projectElement);
    }

    Element simulationElement = new Element("simulation");
    simulationElement.addContent(getConfigXML());
    root.addContent(simulationElement);

    // Create started plugins config
    Collection<Element> pluginsConfig = getPluginsConfigXML();
    if (pluginsConfig != null) {
      root.addContent(pluginsConfig);
    }

    return root;
  }
  public Collection<Element> getPluginsConfigXML() {
    ArrayList<Element> config = new ArrayList<Element>();
    Element pluginElement, pluginSubElement;

    System.out.println("getPluginsConfigXML loop");
    /* Loop over all plugins */
    for (Plugin startedPlugin : startedPlugins) {
     System.out.println("getPluginsConfigXML loop");
      int pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();

      // Ignore GUI plugins
      if (pluginType == PluginType.COOJA_PLUGIN
          || pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        continue;
      }

      pluginElement = new Element("plugin");
      pluginElement.setText(startedPlugin.getClass().getName());

      // Create mote argument config (if mote plugin)
      if (pluginType == PluginType.MOTE_PLUGIN) {
        pluginSubElement = new Element("mote_arg");
        Mote taggedMote = ((MotePlugin) startedPlugin).getMote();
        for (int moteNr = 0; moteNr < getMotesCount(); moteNr++) {
          if (getMote(moteNr) == taggedMote) {
            pluginSubElement.setText(Integer.toString(moteNr));
            pluginElement.addContent(pluginSubElement);
            break;
          }
        }
      }

      // Create plugin specific configuration
      Collection<Element> pluginXML = startedPlugin.getConfigXML();
      if (pluginXML != null) {
        pluginSubElement = new Element("plugin_config");
        pluginSubElement.addContent(pluginXML);
        pluginElement.addContent(pluginSubElement);
      }

      // If plugin is visualizer plugin, create visualization arguments
      if (startedPlugin.getGUI() != null) {
        JInternalFrame pluginFrame = startedPlugin.getGUI();

        pluginSubElement = new Element("width");
        pluginSubElement.setText("" + pluginFrame.getSize().width);
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("z");
        pluginSubElement.setText("" + getDesktopPane().getComponentZOrder(pluginFrame));
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("height");
        pluginSubElement.setText("" + pluginFrame.getSize().height);
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("location_x");
        pluginSubElement.setText("" + pluginFrame.getLocation().x);
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("location_y");
        pluginSubElement.setText("" + pluginFrame.getLocation().y);
        pluginElement.addContent(pluginSubElement);

        if (pluginFrame.isIcon()) {
          pluginSubElement = new Element("minimized");
          pluginSubElement.setText("" + true);
          pluginElement.addContent(pluginSubElement);
        }
      }

      config.add(pluginElement);
    }

    return config;
  }

  private Plugin startPlugin(final Class<? extends Plugin> pluginClass,
      final GUI argGUI, final Simulation argSimulation, final Mote argMote, boolean activate)
  throws PluginConstructionException
  {

    // Check that plugin class is registered
/*
    if (!pluginClasses.contains(pluginClass)) {
      throw new PluginConstructionException("Tool class not registered: " + pluginClass);
    }
*/
    // Construct plugin depending on plugin type
    int pluginType = pluginClass.getAnnotation(PluginType.class).value();
    System.out.println("pluginType value"+pluginType);
    Plugin plugin;

    try {
      if (pluginType == PluginType.MOTE_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for mote plugin");
        }
        if (argSimulation == null) {
          throw new PluginConstructionException("No simulation argument for mote plugin");
        }
        if (argMote == null) {
          throw new PluginConstructionException("No mote argument for mote plugin");
        }

        plugin =
          pluginClass.getConstructor(new Class[] { Mote.class, Simulation.class, GUI.class })
          .newInstance(argMote, argSimulation, argGUI);

      } else if (pluginType == PluginType.SIM_PLUGIN
          || pluginType == PluginType.SIM_STANDARD_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for simulation plugin");
        }
        if (argSimulation == null) {
          throw new PluginConstructionException("No simulation argument for simulation plugin");
        }

        
        plugin =
          pluginClass.getConstructor(new Class[] { Simulation.class, GUI.class})
          .newInstance(argSimulation, argGUI);

      } else if (pluginType == PluginType.COOJA_PLUGIN
          || pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for GUI plugin");
        }

        plugin =
          pluginClass.getConstructor(new Class[] { GUI.class })
          .newInstance(argGUI);

      } else {
        throw new PluginConstructionException("Bad plugin type: " + pluginType);
      }
    } catch (PluginRequiresVisualizationException e) {
      PluginConstructionException ex = new PluginConstructionException("Tool class requires visualization: " + pluginClass.getName());
      ex.initCause(e);
      throw ex;
    } catch (Exception e) {
      PluginConstructionException ex = new PluginConstructionException("Construction error for tool of class: " + pluginClass.getName());
      ex.initCause(e);
      throw ex;
    }

    if (activate) {
      plugin.startPlugin();
    }

    // Add to active plugins list
    startedPlugins.add(plugin);
    //updateGUIComponentState();

/*
    // Show plugin if visualizer type
    if (activate && plugin.getGUI() != null) {
      myGUI.showPlugin(plugin);
    }
*/

    return plugin;
  }
 public class PluginConstructionException extends Exception {
                private static final long serialVersionUID = 8004171223353676751L;
                public PluginConstructionException(String message) {
      super(message);
    }
  }


 public void removePlugin(final Plugin plugin, final boolean askUser) {
    new RunnableInEDT<Boolean>() {
      public Boolean work() {
        /* Free resources */
        plugin.closePlugin();
        startedPlugins.remove(plugin);
        //updateGUIComponentState();

        /* Dispose visualized components */
        if (plugin.getGUI() != null) {
          plugin.getGUI().dispose();
        }

        /* (OPTIONAL) Remove simulation if all plugins are closed */
/*
        if (mySimulation.getSimulation() != null && askUser && startedPlugins.isEmpty()) {
          doRemoveSimulation(true);
        }
*/

        return true;
      }
    }.invokeAndWait();
  }

 public void stopAllPlugin()
{
      for (Plugin p: startedPlugins.toArray(new Plugin[0])) {
          removePlugin(p, false);
      } 
} 

  public static abstract class RunnableInEDT<T> {
    private T val;

    /**
     * Work method to be implemented.
     *
     * @return Return value
     */
    public abstract T work();

    /**
     * Runs worker method in event dispatcher thread.
     *
     * @see #work()
     * @return Worker method return value
     */
    public T invokeAndWait() {
      if(java.awt.EventQueue.isDispatchThread()) {
        return RunnableInEDT.this.work();
      }

      try {
        java.awt.EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            val = RunnableInEDT.this.work();
          }
        });
  } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }

      return val;
    }
  }

  public static void main(String args[])
  {
      String fullFile = new String() ;
      String testCaseName = new String();
      boolean SimControl = false;
      boolean TimeLine = false;
      //GenTestcsc test = new GenTestcsc();
 try{
 fullFile = new String(Files.readAllBytes(Paths.get("GenTest.txt")));
 } catch (Exception e) { e.toString();}
 Pattern pat = Pattern.compile("[\\(\\)]");
 String strs[] = pat.split(fullFile);
 for(int i=0; i<strs.length;i++)
 {
   System.out.println("strs[ "+i+"]"+strs[i]);
   if(strs[i].length() < 10) continue;
   Pattern pat1 = Pattern.compile("[\\{\\}]");
   String strs1[] = pat1.split(strs[i].trim());
   for (int j=0; j<strs1.length;j++)
   {
     System.out.println("Next token :"+strs1[j].trim());
     Pattern pat2 = Pattern.compile("[\\,]");
     if(strs1[j].contains("testcasename"))
     {
       String strs2[] = pat2.split(strs1[j].trim());
       System.out.println("Test case name is "+strs2[1]);
       testCaseName=strs2[1].trim();
     }
     if(strs1[j].contains("title"))
     {
      String strs2[] = pat2.split(strs1[j].trim());
      Element temp = new Element("title");
      temp.setText(strs2[1]);
      config.add(temp);
     }
     if(strs1[j].contains("radiomedium"))
     {
      String strs2[] = pat2.split(strs1[j].trim()); 
      Element temp1 = new Element("radiomedium");
      temp1.setText(strs2[1]);
      config.add(temp1);
     }
     if(strs1[j].contains("motetype") && !strs1[j].contains("mote1"))
     {
      String strs2[] = pat2.split(strs1[j].trim()); 
      for(int k=0;k<strs2.length;k++)
      System.out.println("Strs2 "+strs2[k]);
      Element temp2 = new Element("motetype");
      temp2.setText(strs2[1].trim());
      Element sourceElem = new Element("source");
      sourceElem.setText(strs2[3].trim());
      Element identifierElem = new Element("identifier");
      identifierElem.setText(strs2[5].trim());
      temp2.addContent(sourceElem);
      temp2.addContent(identifierElem);
      config.add(temp2);
     }
     if(strs1[j].contains("mote1"))
     {

      String strs2[] = pat2.split(strs1[j].trim()); 
      Element temp3 = new Element("mote");
      temp3.setText(strs2[1].trim());
      Element temp4 = new Element("motetype_identifier");
      temp4.setText(strs2[3].trim());
      temp3.addContent(temp4);
      config.add(temp3);
     }
    if(strs1[j].contains("SimControl"))
    {
      SimControl = true;
    }
    if(strs1[j].contains("TimeLine"))
    {
      TimeLine = true;
    }
    }
      System.out.println("config to string"+config.toString());

      Long manualRandomSeed = new Long(1);
      try{ 
      GenTestcsc test = new GenTestcsc();
      try{
 
      myGUI.setVisualizedInFrame(false);
      Class pluginClass = Class.forName("se.sics.cooja.plugins.SimControl");
      Class pluginClass1 = Class.forName("se.sics.cooja.plugins.TimeLine");
      //test.startPlugin(pluginClass,myGUI,mySimulation,null,true);
      //test.startPlugin(pluginClass1,myGUI,mySimulation,null,true);
    if(SimControl)
    {
      //test.startPlugin(pluginClass,myGUI,mySimulation,null,true);
    }
    if(TimeLine)
    {
      //test.startPlugin(pluginClass1,myGUI,mySimulation,null,true);
    }
      } catch( Exception e)
      {
       System.out.println("Exception while starting the plugin: " + e);
       e.printStackTrace();
      }            
      File file = new File(testCaseName);
      file.createNewFile();
      test.setConfigXML(config,false,manualRandomSeed);
      test.saveSimulationConfig(file);
      System.out.println("After stop simulation");
      mySimulation.stopSimulation();
      myGUI.doRemoveSimulation(false);
      test.stopAllPlugin();
      config = new ArrayList<Element>();

      } catch (Exception e)
      {
       System.out.println("Exception while saving simulation config: " + e);
       e.printStackTrace();
      }
   }
  }
}
