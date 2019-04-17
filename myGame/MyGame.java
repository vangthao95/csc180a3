package myGame;

import myGameEngine.*;
// --------------From DolphinClick---------------
//import java.awt.*; // Split into Color, DisplayMode, and GraphicsEnvironment
import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.awt.event.*;
import java.io.*;

import ray.rage.*;
import ray.rage.game.*;
import ray.rage.rendersystem.*;
import ray.rage.rendersystem.Renderable.*;
import ray.rage.scene.*;
import ray.rage.scene.Camera.Frustum.*;
import ray.rage.scene.controllers.*;
import ray.rml.*;
import ray.rage.rendersystem.gl4.GL4RenderSystem;
// -----------------------------------------------

// ----------------From InputActions--------------
import ray.rage.rendersystem.states.*;
import ray.rage.asset.texture.*;
import ray.input.*;
import ray.input.action.*;
import java.awt.geom.*;
// -----------------------------------------------

import ray.rage.rendersystem.shader.GpuShaderProgram; // For GpuShaderProgram
import java.nio.*; // For FloatBuffer and IntBuffer
import ray.rage.util.*; // For BufferUtil direct float and int buffers
import java.util.Random; // For random numbers

import myGameEngine.*;

import net.java.games.input.Controller;

// Networking begin
import ray.networking.IGameConnection.ProtocolType;
import java.util.Vector;
import java.util.UUID;
import java.net.InetAddress;
import java.net.UnknownHostException;
// Networking end

// Scripting begin
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.util.List;
// Scripting end

public class MyGame extends VariableFrameRateGame {
	// Script files
	File rotationD2RC = new File("scripts/InitParams.js");
	File helloWorldS = new File("scripts/hello.js");
	// End of script files

	// Variables associated with scripts
	ScriptEngineManager factory = new ScriptEngineManager();
	ScriptEngine jsEngine = factory.getEngineByName("js"); // Game engine
	RotationController dolphin2RC; // InitParam.js
	Long rotationD2RCLastModifiedTime; // Modified time for rotationD2RC script
	
	// End of variables associated with scripts

	// to minimize variable allocation in update()
	Camera3Pcontroller orbitController1, orbitController2;
	StretchController player1controller;
	CustomController player2controller;
	GL4RenderSystem rs;
	private float elapsTime = 0.0f;
	private int counter, score = 0;
	private Camera camera;
	private SceneNode dolphinN1, dolphinN2;
	private SceneNode cameraN1;
	// skybox
	private static final String SKYBOX_NAME = "MySkyBox";
	private boolean skyBoxVisible = true;

	String kbName;
	
	private InputManager im;
	
	// Networking begin
	private String serverAddress;
	private int serverPort;
	private ProtocolType serverProtocol;
	private ProtocolClient protClient;
	private boolean isClientConnected;
	private Vector<UUID> gameObjectsToRemove;
	// Networking end
	
	private SceneManager sceneManager;
	private int uniqueGhosts = 0;
	
	// Terrain Variables
	private SceneNode tessN;
	private Tessellation tessE;
	// End of terrain variables
	
    public MyGame(String serverAddr, int sPort)
	{
        super();
		this.serverAddress = serverAddr;
		this.serverPort = sPort;
		this.serverProtocol = ProtocolType.UDP;
		
		System.out.println("press w, a, s, d to move the avatar");
		System.out.println("press the up, down, left, and right arrow to move the camera");
		
	}

    public static void main(String[] args)
	{
        Game game = new MyGame(args[0], Integer.parseInt(args[1]));
        try {
            game.startup();
            game.run();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            game.shutdown();
            game.exit();
        }
		
    }

	private void executeScript(File scriptFileName)
	{
		try
		{
			FileReader fileReader = new FileReader(scriptFileName);
			jsEngine.eval(fileReader); //execute the script statements in the file
			fileReader.close();
		}
		catch (FileNotFoundException e1)
		{
			System.out.println(scriptFileName + " not found " + e1);
		}
		catch (IOException e2)
		{
			System.out.println("IO problem with " + scriptFileName + e2);
		}
		catch (ScriptException e3)
		{
			System.out.println("ScriptException in " + scriptFileName + e3);
		}
		catch (NullPointerException e4)
		{
			System.out.println ("Null ptr exception in " + scriptFileName + e4);
		}
	}
	
	
	private void setupNetworking()
	{
		gameObjectsToRemove = new Vector<UUID>();
		isClientConnected = false;
		try
		{
			protClient = new ProtocolClient(InetAddress.getByName(serverAddress), serverPort, serverProtocol, this);
		}
		catch (UnknownHostException e) 
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if (protClient == null)
		{
			System.out.println("missing protocol host");
		}
		else
		{
			// ask client protocol to send initial join message
			//to server, with a unique identifier for this client
			protClient.sendJoinMessage();
		}
	}
	
	public void setIsConnected(boolean value)
	{
		isClientConnected = value;
	}
	
	protected void processNetworking(float elapsTime)
	{
		// Process packets received by the client from the server
		if (protClient != null)
		{
			//System.out.println("Telling protocol client to process packets...");
			protClient.processPackets();
		}
		
		// remove ghost avatars for players who have left the game
		/*Iterator<UUID> it = gameObjectsToRemove.iterator();
		while(it.hasNext())
		{
			//sm.destroySceneNode(it.next().toString());
		}
		gameObjectsToRemove.clear();*/
	}
	
	public void addGhostAvatarToGameWorld(GhostAvatar avatar, Vector3 pos)
	throws IOException
	{
		if (avatar != null)
		{
			uniqueGhosts++;
			Entity ghostE = sceneManager.createEntity("ghost" + uniqueGhosts, "dolphinHighPoly.obj");
			ghostE.setPrimitive(Primitive.TRIANGLES);
			SceneNode ghostN = sceneManager.getRootSceneNode().createChildSceneNode(avatar.getID().toString());
			ghostN.attachObject(ghostE);
			ghostN.setLocalPosition(pos);
			avatar.setNode(ghostN);
		}
	}
	
	public void removeGhostAvatarFromGameWorld(GhostAvatar avatar)
	{
		//if(avatar != null) gameObjectsToRemove.add(avatar.getID());
	}
	
	/*private class SendCloseConnectionPacketAction extends AbstractInputAction
	{
		// for leaving the game... need to attach to an input device
		@Override
		public void performAction(float time, Event e)
		{
			if(protClient != null && isClientConnected == true)
			{
				protClient.sendByeMessage();
			}
		}
	}*/
	
    @Override
    protected void setupScene(Engine eng, SceneManager sm) throws IOException
	{
		/*
    	ScriptEngineManager factory = new ScriptEngineManager();
		// get a list of the script engines on this platform
		List<ScriptEngineFactory> list = factory.getEngineFactories();
		System.out.println("Script Engine Factories found:");
		for (ScriptEngineFactory f : list)
		{
			System.out.println(" Name = " + f.getEngineName()
			+ " language = " + f.getLanguageName()
			+ " extensions = " + f.getExtensions());
		}*/
		
		// run hello world script
		executeScript(helloWorldS);
		
		// Run the InitParams.js script to initialize spinSpeed
		executeScript(rotationD2RC);
		rotationD2RCLastModifiedTime = rotationD2RC.lastModified();
		// Initialize the rotation controller with the variable spinSpeed
		dolphin2RC = new RotationController(Vector3f.createUnitVectorY(),
				((Double)(jsEngine.get("spinSpeed"))).floatValue());
    	
    	// set up sky box
    	Configuration conf = eng.getConfiguration();
    	TextureManager tm = getEngine().getTextureManager();
    	tm.setBaseDirectoryPath(conf.valueOf("assets.skyboxes.path"));
    	Texture front = tm.getAssetByPath("zpos.png");
    	Texture back = tm.getAssetByPath("zneg.png");
    	Texture left = tm.getAssetByPath("xneg.png");
    	Texture right = tm.getAssetByPath("xpos.png");
    	Texture top = tm.getAssetByPath("ypos.png");
    	Texture bottom = tm.getAssetByPath("yneg.png");
    	 tm.setBaseDirectoryPath(conf.valueOf("assets.textures.path"));
    	 
    	// cubemap textures are flipped upside-down.
    	// All textures must have the same dimensions, so any image�s
    	// heights will work since they are all the same height
    	AffineTransform xform = new AffineTransform();
    	xform.translate(0, front.getImage().getHeight());
    	xform.scale(1d, -1d);
    	front.transform(xform);
    	back.transform(xform);
    	left.transform(xform);
    	right.transform(xform);
    	top.transform(xform);
    	bottom.transform(xform);
    	SkyBox sb = sm.createSkyBox(SKYBOX_NAME);
    	sb.setTexture(front, SkyBox.Face.FRONT);
    	sb.setTexture(back, SkyBox.Face.BACK);
    	sb.setTexture(left, SkyBox.Face.LEFT);
    	sb.setTexture(right, SkyBox.Face.RIGHT);
    	sb.setTexture(top, SkyBox.Face.TOP);
    	sb.setTexture(bottom, SkyBox.Face.BOTTOM);
    	sm.setActiveSkyBox(sb);
    	
    	
    	
		sceneManager = sm;
		
		Entity dolphinE1 = sm.createEntity("dolphinE1", "avatar_v1.obj");
        dolphinE1.setPrimitive(Primitive.TRIANGLES);
        
		Entity dolphinE2 = sm.createEntity("dolphinE2", "exporting-uv.obj");
        dolphinE1.setPrimitive(Primitive.TRIANGLES);

        dolphinN1 = sm.getRootSceneNode().createChildSceneNode(dolphinE1.getName() + "Node");
        dolphinN1.moveBackward(.5f);
		dolphinN1.scale(0.05f, 0.05f, 0.05f);
        dolphinN1.attachObject(dolphinE1);
		dolphinN1.moveUp(.25f);
        
        dolphinN2 = sm.getRootSceneNode().createChildSceneNode(dolphinE2.getName() + "Node");
        dolphinN2.moveForward(1.0f);
        dolphinN2.attachObject(dolphinE2);
        dolphinN2.moveUp(.25f);
		// Add dolphin 2 to rotation controller
		dolphin2RC.addNode(dolphinN2);
		sm.addController(dolphin2RC);

        sm.getAmbientLight().setIntensity(new Color(.2f, .2f, .2f));
	
        player1controller = new StretchController();
		player2controller = new CustomController();
		sm.addController(player1controller);
		sm.addController(player2controller);
		// setupPlanets(eng, sm);
		// setupManualObjects(eng, sm);
		setupNetworking();
		setupInputs();
		setupOrbitCamera(eng, sm);
		tessE = sm.createTessellation("tessE", 6);
		// subdivisions per patch: min=0, try up to 32
		tessE.setSubdivisions(8f);
		tessN = sm.getRootSceneNode().createChildSceneNode("TessN");
		tessN.attachObject(tessE);
		tessN.scale(20, 40, 20);
		tessE.setHeightMap(this.getEngine(), "heightmap1.jpeg");
		tessE.setTexture(this.getEngine(), "hexagons.jpeg");
    }
    
    protected void setupInputs()
    {
    	im =  new GenericInputManager();
    	String gpName = im.getFirstGamepadName();
    	
		Action quitGameAction = new QuitGameAction(this);
		Action incrementCounter = new IncrementCounterAction(this);
		Action CameraLookLeftRightA = new CameraLookLeftRightAction(camera);
		Action cameraLookUpDownA = new CameraLookUpDownAction(camera);
		Action p1MoveForwardA = new MoveForwardAction(dolphinN1, protClient, this);
		Action p1MoveBackwardA = new MoveBackwardAction(dolphinN1, protClient, this);
		Action p1MoveLeftA = new MoveLeftAction(dolphinN1, protClient, this);
		Action p1MoveRightA = new MoveRightAction(dolphinN1, protClient, this);

		Action p2MoveVerticalA = new ControllerMoveHorizontalAction(dolphinN2);
		Action p2MoveHorizontalA = new ControllerMoveVerticalAction(dolphinN2);
    	
		for (int i = 0; i < 10; i++)
		{
			Controller keyboard = im.getKeyboardController(i);
			if (keyboard == null)
				continue;

			
			im.associateAction(keyboard,
					net.java.games.input.Component.Identifier.Key.W,
					p1MoveForwardA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
					
			im.associateAction(keyboard,
					net.java.games.input.Component.Identifier.Key.S,
					p1MoveBackwardA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
					
			im.associateAction(keyboard,
					net.java.games.input.Component.Identifier.Key.A,
					p1MoveLeftA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
					
			im.associateAction(keyboard,
					net.java.games.input.Component.Identifier.Key.D,
					p1MoveRightA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
			
		}
    	//im.associateAction(kbName,net.java.games.input.Component.Identifier.Key.ESCAPE,quitGameAction,InputManager.INPUT_ACTION_TYPE.ON_PRESS_ONLY);
		//im.associateAction(kbName,net.java.games.input.Component.Identifier.Key.I,quitGameAction,InputManager.INPUT_ACTION_TYPE.ON_PRESS_ONLY);
		//im.associateAction(kbName,net.java.games.input.Component.Identifier.Key.C,incrementCounter,InputManager.INPUT_ACTION_TYPE.ON_PRESS_ONLY);
		for (int i = 0; i < 10; i++)
		{
			Controller consoleController = im.	getGamepadController(i);
			if (consoleController == null)
				continue;
			
			im.associateAction(consoleController,
					net.java.games.input.Component.Identifier.Axis.Y,
					p2MoveVerticalA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
				
			im.associateAction(consoleController,
					net.java.games.input.Component.Identifier.Axis.X,
					p2MoveHorizontalA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
			/*	
			im.associateAction(consoleController,
					net.java.games.input.Component.Identifier.Key.A,
					p1MoveLeftA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
					
			im.associateAction(consoleController,
					net.java.games.input.Component.Identifier.Key.D,
					p1MoveRightA,
					InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);*/
		}
		
		/*
		// Camera look left and right
		im.associateAction(gpName,
				net.java.games.input.Component.Identifier.Axis.RX,
				CameraLookLeftRightA,
				InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN );
		
		// Camera look up and down
		im.associateAction(gpName,
				net.java.games.input.Component.Identifier.Axis.RY,
				cameraLookUpDownA,
				InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN );

		*/
		// Controller camera look left
		/*im.associateAction(gpName,
				net.java.games.input.Component.Identifier.Button._0,
				p1MoveForwardA,
				InputManager.INPUT_ACTION_TYPE.REPEAT_WHILE_DOWN);
  */
    }
	
	@Override
	protected void setupWindow(RenderSystem rs, GraphicsEnvironment ge) {
		rs.createRenderWindow(new DisplayMode(1000, 700, 24, 60), false);
	}

    @Override
    protected void setupCameras(SceneManager sm, RenderWindow rw) {	   	
    	SceneNode rootNode = sm.getRootSceneNode();
    	camera = sm.createCamera("MainCamera",Projection.PERSPECTIVE);
    	rw.getViewport(0).setCamera(camera);
		camera.setRt((Vector3f)Vector3f.createFrom(1.0f, 0.0f, 0.0f));
		camera.setUp((Vector3f)Vector3f.createFrom(0.0f, 1.0f, 0.0f));
		camera.setFd((Vector3f)Vector3f.createFrom(0.0f, 0.0f, -1.0f));
		camera.setPo((Vector3f)Vector3f.createFrom(0.0f, 0.0f, 0.0f));
    	cameraN1 = rootNode.createChildSceneNode("MaincameraN1");
    	cameraN1.attachObject(camera);
    	camera.getFrustum().setFarClipDistance(1000.0f);
		camera.setMode('n');
    }
	
    protected void setupOrbitCamera(Engine eng, SceneManager sm)
    {
    	String gpName = im.getFirstGamepadName();
		String kbName = im.getKeyboardName();
		orbitController1 = new Camera3Pcontroller(camera, cameraN1, dolphinN1, kbName, im);
    }
	
	public void incrementCounter()
	{
		counter++;
	}

	public Vector3 getPlayerPosition()
	{
		return dolphinN1.getLocalPosition();
	}
	
	@Override
    protected void update(Engine engine) {
		im.update(elapsTime);
		processNetworking(elapsTime);
		orbitController1.updateCameraPosition();
		//orbitController2.updateCameraPosition();
		rs = (GL4RenderSystem) engine.getRenderSystem();
		elapsTime += engine.getElapsedTimeMillis();
		
		// run script again in update() to demonstrate dynamic modification
		long modTime = rotationD2RC.lastModified();
		if (modTime > rotationD2RCLastModifiedTime)
		{
			rotationD2RCLastModifiedTime = modTime;
			executeScript(rotationD2RC);
			dolphin2RC.setSpeed(((Double)(jsEngine.get("spinSpeed"))).floatValue());
			System.out.println("Dolphin 2 rotation speed updated");
		}
	} // End of update()
	
	public void updateVerticalPosition()
	{
		// dolphinN1
		//SceneNode tessN = this.getEngine().getSceneManager().getSceneNode("tessN");
		//Tessellation tessE = ((Tessellation) tessN.getAttachedObject("tessE"));
		// Figure out Avatar's position relative to plane
		Vector3 worldAvatarPosition = dolphinN1.getWorldPosition();
		Vector3 localAvatarPosition = dolphinN1.getLocalPosition();
		
		// use avatar World coordinates to get coordinates for height
		Vector3 newAvatarPosition = Vector3f.createFrom(
			// Keep the X coordinate
			localAvatarPosition.x(),
			// The Y coordinate is the varying height
			tessE.getWorldHeight(
			worldAvatarPosition.x(),
			worldAvatarPosition.z()),
			//Keep the Z coordinate
			localAvatarPosition.z()
		);
		
		// use avatar Local coordinates to set position, including height
		dolphinN1.setLocalPosition(newAvatarPosition);
	}
}
