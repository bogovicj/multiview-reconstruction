package net.preibisch.mvrecon.fiji.datasetmanager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import fiji.util.gui.GenericDialogPlus;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataIOException;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import util.URITools;

public class SmartSPIM implements MultiViewDatasetDefinition
{
	public static String defaultMetadataFile = "";
	public static boolean defaultConfirmFiles = true;

	@Override
	public String getTitle()
	{
		return "SmartSPIM Datasets";
	}

	@Override
	public String getExtendedDescription()
	{
		return "This datset definition supports folder structures saved by SmartSPIM's.";
	}

	@Override
	public SpimData2 createDataset( final String xmlFileName )
	{
		final Pair< URI, Boolean > md = queryMetaDataFile();

		if ( md == null )
			return null;

		final SmartSPIMMetaData metadata = parseMetaDataFile( md.getA() );

		if ( metadata == null )
			return null;

		populateImageSize( metadata, md.getB() );

		// TODO Auto-generated method stub
		return null;
	}

	public static boolean populateImageSize( final SmartSPIMMetaData metadata, final boolean confirmAllImages )
	{
		metadata.dimensions = null;
		metadata.sortedFileNames = null;

		for ( int channel = 0; channel < metadata.channels.size(); ++ channel )
			for ( int xTile = 0; xTile < metadata.xTileLocations.size(); ++ xTile)
				for ( int yTile = 0; yTile < metadata.yTileLocations.size(); ++ yTile)
				{
					final URI imageDir = metadata.folderFor(
							metadata.channels.get( channel ),
							metadata.xTileLocations.get( xTile ),
							metadata.yTileLocations.get( yTile ) );

					IOFunctions.println( "Directory: " + imageDir );

					final Pair< long[], List< String > > stackData =
							metadata.loadImageSize( channel, xTile, yTile );

					final long[] dimensions = stackData.getA();

					IOFunctions.println( "dimensions: " + Util.printCoordinates( dimensions ) );

					if ( dimensions == null )
						return false;

					if ( metadata.dimensions == null )
					{
						metadata.dimensions = dimensions;
						metadata.sortedFileNames = stackData.getB();
					}

					if ( !confirmAllImages )
						return true;

					if ( !Arrays.equals( metadata.dimensions, dimensions ) )
					{
						IOFunctions.println( "dimensions are not equal. Stopping: " + Util.printCoordinates( dimensions ) + ", " + Util.printCoordinates( metadata.dimensions ) );
						return false;
					}

					if ( !areListsEqual( metadata.sortedFileNames, stackData.getB() ) )
					{
						IOFunctions.println( "file names are not equal. Stopping." );
						return false;
					}
					
				}

		return true;
	}

	public static SmartSPIMMetaData parseMetaDataFile( final URI md )
	{
		IOFunctions.println( "Parsing: " + md + " ... ");

		final SmartSPIMMetaData metadata;

		try
		{
			metadata = new SmartSPIMMetaData( md );
		}
		catch (SpimDataIOException e)
		{
			IOFunctions.println( "Cannot extract directory for '" + md + "': " + e );
			e.printStackTrace();
			return null;
		}

		if ( !URITools.isFile( md ) )
		{
			IOFunctions.println( "So far only local file systems are supported.");
			return null;
		}

		final File file = new File( md );

		if ( !file.exists() )
		{
			IOFunctions.println( "Error: " + file + " does not exist.");
			return null;
		}

		// Open the file using a GsonReader
		try (JsonReader reader = new JsonReader( new FileReader( file ) ) )
		{
			final Gson gson = new Gson();

			final JsonElement e = gson.fromJson(reader, (JsonElement.class ));
			final JsonElement session_config = e.getAsJsonObject().get("session_config");
			final JsonElement tile_config = e.getAsJsonObject().get("tile_config");

			// we cannot directly de-serialize the session_config because it contains special characters in the field names
			final Map<String, JsonElement> sessionMap = session_config.getAsJsonObject().asMap();

			for ( final Entry< String, JsonElement > entry : sessionMap.entrySet() )
			{
				if ( entry.getKey().contains( "Z step" ) )
					metadata.zRes = Double.parseDouble( entry.getValue().getAsString() );

				if ( entry.getKey().contains( "m/pix" ) )
					metadata.xyRes = Double.parseDouble( entry.getValue().getAsString() );
			}

			IOFunctions.println( "resolution: " + metadata.xyRes + " x " + metadata.xyRes + " x " + metadata.zRes + " um/px." );

			final Type typeTileConfig = new TypeToken<HashMap<String, SmartSPIM_Tile>>() {}.getType();
			metadata.tiles = gson.fromJson(tile_config, typeTileConfig);

			IOFunctions.println( "number of tiles: " + metadata.tiles.size() );

			metadata.channels = SmartSPIM_Tile.channels( metadata.tiles.values() );
			metadata.xTileLocations = SmartSPIM_Tile.xTileLocations( metadata.tiles.values() );
			metadata.yTileLocations = SmartSPIM_Tile.yTileLocations( metadata.tiles.values() );
			metadata.zOffsets = SmartSPIM_Tile.zOffsets( metadata.tiles.values() );

			IOFunctions.println( "channels: " );
			metadata.channels.forEach( p -> IOFunctions.println( "\t" + SmartSPIM_Tile.channelToFolderName( p ) ) );

			IOFunctions.println( "x tile locations: " );
			metadata.xTileLocations.forEach( x -> IOFunctions.println( "\t" + x ) );

			IOFunctions.println( "y tile locations: " );
			metadata.yTileLocations.forEach( y -> IOFunctions.println( "\t" + y ) );

			IOFunctions.println( "z offset(s): " );
			metadata.zOffsets.forEach( y -> IOFunctions.println( "\t" + y ) );

			if ( metadata.zOffsets.size() != 1 )
			{
				IOFunctions.println( "multiple z offsets not supported yet. please contact developers." );
				return null;
			}
		}
		catch (IOException e)
		{
			IOFunctions.println("Error reading the file: " + e.getMessage());
			return null;
		}

		return metadata;
	}

	protected Pair< URI, Boolean > queryMetaDataFile()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Define SmartSPIM Dataset" );

		gd.addFileField( "SmartSPIM_metadata.json file", defaultMetadataFile, 50 );
		gd.addCheckbox( "Confirm_presence of all folders & files", defaultConfirmFiles );
		gd.addMessage( "Note: for now we assume the same dimensions of all tiles & channels", GUIHelper.smallStatusFont, GUIHelper.neutral );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final URI metaDataFile = URITools.toURI( defaultMetadataFile = gd.getNextString() );
		final boolean confirmFiles = defaultConfirmFiles = gd.getNextBoolean();

		return new ValuePair<>( metaDataFile, confirmFiles );
	}

	@Override
	public MultiViewDatasetDefinition newInstance()
	{
		return new SmartSPIM();
	}


	public static boolean areListsEqual( final List< String > array1, final List< String > array2 )
	{
		if (array1 == null || array2 == null)
			return false;

		if (array1.size() != array2.size() )
			return false;

		for ( int i = 0; i < array1.size(); ++i )
			if ( !array1.get( i ).equals(array2.get( i )) )
				return false;

		return true;
	}

	public static class SmartSPIMMetaData
	{
		final public URI metadataFile, dir;

		public long[] dimensions;

		public List< String > sortedFileNames;

		public double xyRes = -1;
		public double zRes = -1;

		public HashMap<String, SmartSPIM_Tile> tiles;
		public List<Pair<Integer, Integer>> channels;
		public List<Long> xTileLocations;
		public List<Long> yTileLocations;
		public List<Long> zOffsets;

		public SmartSPIMMetaData( final URI metadataFile ) throws SpimDataIOException
		{
			this.metadataFile = metadataFile;
			this.dir = URITools.getParentURI( this.metadataFile );
		}

		public URI folderFor( final Pair<Integer, Integer> channel, final long xTile, final long yTile )
		{
			return folderFor( dir, channel, xTile, yTile );
		}

		public List< String > sortedSlicesFor( final Pair<Integer, Integer> channel, final long xTile, final long yTile )
		{
			final URI dir = folderFor( channel, xTile, yTile );

			return sortedSlicesFor( dir );
		}

		public ImagePlus loadImage( final Pair<Integer, Integer> channel, final long xTile, final long yTile, final String fileName )
		{
			return loadImage( dir, channel, xTile, yTile, fileName );
		}

		protected Pair< long[], List< String > > loadImageSize( final int channel, final int xTile, final int yTile )
		{
			return loadImageSize( this, channel, xTile, yTile );
		}

		public List< String > sortedSlicesFor( final URI dir )
		{
			return Arrays
				.asList(new File(dir).list((directory, name) -> name.toLowerCase().matches("\\d+\\.tif{1,2}")))
				.stream().sorted()
				.collect(Collectors.toList());
		}

		protected static Pair< long[], List< String > > loadImageSize( final SmartSPIMMetaData metadata, final int channel, final int xTile, final int yTile )
		{
			final URI imageDir = metadata.folderFor(
					metadata.channels.get( channel ),
					metadata.xTileLocations.get( xTile ),
					metadata.yTileLocations.get( yTile ) );

			final List<String> files = metadata.sortedSlicesFor( imageDir );
			final ImagePlus imp = SmartSPIMMetaData.loadImage( imageDir, files.get( 0 ) );

			if ( imp.getProcessor() == null )
			{
				IOFunctions.println( "Failed to load image. Stopping." );
				return null;
			}

			final long[] dim =  new long[] { imp.getWidth(), imp.getHeight(), files.size() };

			imp.close();

			return new ValuePair<>( dim, files );
		}

		public static URI folderFor( final URI dir, final Pair<Integer, Integer> channel, final long xTile, final long yTile )
		{
			return dir.resolve( SmartSPIM_Tile.channelToFolderName( channel ) + "/" + xTile + "/" + xTile + "_" + yTile + "/" );
		}

		public static ImagePlus loadImage( final URI dir, final Pair<Integer, Integer> channel, final long xTile, final long yTile, final String fileName )
		{
			final URI imageDir = SmartSPIMMetaData.folderFor( dir, channel, xTile, yTile );
			
			return loadImage( imageDir, fileName );
		}

		public static ImagePlus loadImage( final URI imageDir, final String fileName )
		{
			final File file = new File( imageDir.resolve( fileName ) );
			final ImagePlus imp = new ImagePlus( file.getAbsolutePath() );

			return imp;
		}
	}

	public static class SmartSPIM_Tile
	{
		public long X;
		public long Y;
		public long Z;
		public int Laser;
		public int Side;
		public int Exposure;
		public int Skip;
		public int Filter;

		public static List< Long > xTileLocations( final Collection< SmartSPIM_Tile > tiles )
		{
			return tiles.stream().map( t -> t.X ).distinct().sorted().collect(Collectors.toList());
		}

		public static List< Long > yTileLocations( final Collection< SmartSPIM_Tile > tiles )
		{
			return tiles.stream().map( t -> t.Y ).distinct().sorted().collect(Collectors.toList());
		}

		public static List< Long > zOffsets( final Collection< SmartSPIM_Tile > tiles )
		{
			return tiles.stream().map( t -> t.Z ).distinct().sorted().collect(Collectors.toList());
		}

		public static List< Pair< Integer, Integer > > channels( final Collection< SmartSPIM_Tile > tiles )
		{
			return tiles.stream().map( t -> new ValuePair<>( t.Laser, t.Filter ) ).distinct().sorted( (o1,o2) -> o1.a.compareTo( o2.a ) ).collect(Collectors.toList());
		}

		public static String channelToFolderName( final Pair< Integer, Integer > channel )
		{
			return "Ex_" + channel.getA() + "_Ch" + channel.getB();
		}
	}

	public static void main( String[] args )
	{
		//parseMetaDataFile( URITools.toURI("/Users/preibischs/Documents/Janelia/Projects/BigStitcher/SmartSPIM/metadata.json"));
		SmartSPIMMetaData metadata =
				parseMetaDataFile( URITools.toURI( "/Volumes/johnsonlab/LM/20241031_11_59_44_RJ_mouse_2_vDisco_hindleg_right_Destripe_DONE/metadata.json") );

		populateImageSize( metadata, true );
	}
}
