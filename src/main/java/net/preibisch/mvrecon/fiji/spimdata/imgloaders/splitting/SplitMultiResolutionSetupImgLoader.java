/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class SplitMultiResolutionSetupImgLoader< T > implements MultiResolutionSetupImgLoader< T >
{
	final MultiResolutionSetupImgLoader< T > underlyingSetupImgLoader;
	final Interval interval;
	final Dimensions size;
	final int n;

	final double[][] mipmapResolutions;
	final AffineTransform3D[] mipmapTransforms;
	final Dimensions[] sizes;
	final Interval[] scaledIntervals;

	private boolean[] isUpdated;

	public SplitMultiResolutionSetupImgLoader( final MultiResolutionSetupImgLoader< T > underlyingSetupImgLoader, final Interval interval )
	{
		this.underlyingSetupImgLoader = underlyingSetupImgLoader;
		this.interval = interval;
		this.n = interval.numDimensions();

		final long[] dim = new long[ interval.numDimensions() ];
		interval.dimensions( dim );

		this.size = new FinalDimensions( dim );

		final int levels = underlyingSetupImgLoader.numMipmapLevels();
		this.sizes = new Dimensions[ levels ];
		this.scaledIntervals = new Interval[ levels ];
		this.mipmapResolutions = underlyingSetupImgLoader.getMipmapResolutions();
		this.mipmapTransforms = underlyingSetupImgLoader.getMipmapTransforms();//new AffineTransform3D[ levels ];

		this.isUpdated = new boolean[ levels ];
		for ( int l = 0; l < levels; ++l )
			this.isUpdated[ l ] = false;

		SplitViewerSetupImgLoader.setUpMultiRes( levels, n, interval, mipmapResolutions, /*mipmapTransforms, */sizes, scaledIntervals/*, underlyingSetupImgLoader.getMipmapTransforms()*/ );
	}

	@Override
	public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
	{
		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getImage( timepointId, hints ), interval ) );
	}

	@Override
	public T getImageType()
	{
		return underlyingSetupImgLoader.getImageType();
	}

	@Override
	public Dimensions getImageSize( final int timepointId )
	{
		return size;
	}

	@Override
	public VoxelDimensions getVoxelSize( final int timepointId )
	{
		return underlyingSetupImgLoader.getVoxelSize( timepointId );
	}

	@Override
	public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final RandomAccessibleInterval< T > full = underlyingSetupImgLoader.getImage( timepointId, level, hints );

		updateScaledIntervals( this.scaledIntervals, level, n, full );

		return Views.zeroMin( Views.interval( full, scaledIntervals[ level ] ) );
	}

	/**
	 * Sometimes because of scaling the max is too high exceeding the actual downsampled image as provided
	 *
	 * @param scaledIntervals - the current scaled intervals (will be updated)
	 * @param level - which level
	 * @param n - num dimensions
	 * @param fullImg - the full interval as currently loaded
	 */
	protected final void updateScaledIntervals( final Interval[] scaledIntervals, final int level, final int n, final Interval fullImg )
	{
		if ( !isUpdated[ level ] )
		{
			synchronized ( this )
			{
				if ( isUpdated[ level ] )
					return;

				isUpdated[ level ] = true;

				boolean updateScaledInterval = false;
		
				for ( int d = 0; d < n; ++d )
					if ( scaledIntervals[ level ].max( d ) >= fullImg.max( d ) )
						updateScaledInterval = true;
		
				if ( updateScaledInterval )
				{
					final long[] min = new long[ n ];
					final long[] max = new long[ n ];
		
					for ( int d = 0; d < n; ++d )
					{
						min[ d ] = scaledIntervals[ level ].min( d );
						max[ d ] = Math.min( scaledIntervals[ level ].max( d ), fullImg.max( d ) );
					}
		
					scaledIntervals[ level ] = new FinalInterval( min, max );
				}
			}
		}
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return mipmapResolutions;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public int numMipmapLevels()
	{
		return underlyingSetupImgLoader.numMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( int timepointId,
			int level, boolean normalize, ImgLoaderHint... hints )
	{
		throw new RuntimeException( "not supported." );
	}

	@Override
	public Dimensions getImageSize( final int timepointId, final int level )
	{
		return sizes[ level ];
	}
}
