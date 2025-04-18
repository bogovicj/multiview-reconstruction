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
package net.preibisch.mvrecon.process.pointcloud.pointdescriptor;

import java.util.ArrayList;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.exception.NoSuitablePointsException;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.matcher.Matcher;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.model.TranslationInvariantModel;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.similarity.SimilarityMeasure;

public class ModelPointDescriptor< P extends Point > extends AbstractPointDescriptor< P, ModelPointDescriptor<P> >
{
	final TranslationInvariantModel<?> model;
	
	/**
	 * Creates a {@link ModelPointDescriptor} that can perform matching using a {@link Model}
	 * 
	 * For the computational process it only modifies the world coordinates of its own descriptorPoints,
	 * thus providing the opportunity for multi-threading.
	 * 
	 * @param basisPoint - the center {@link Point}
	 * @param orderedNearestNeighboringPoints - the {@link Point}s used to create the {@link ModelPointDescriptor}
	 * @param model - the {@link Model} that should be used for matching, it is cloned upon initialization
	 * @param similarityMeasure - which similarity measure
	 * @param matcher - which matcher to use
	 * @throws NoSuitablePointsException if the dimensionality of the {@link Point}s does not fit the {@link Model} or the amount of {@link Point}s for the {@link Model} is too low
	 */
	public ModelPointDescriptor( final P basisPoint, final ArrayList<P> orderedNearestNeighboringPoints, final TranslationInvariantModel<?> model, 
			final SimilarityMeasure similarityMeasure, final Matcher matcher ) throws NoSuitablePointsException
	{
		super( basisPoint, orderedNearestNeighboringPoints, similarityMeasure, matcher );
		
		if ( !model.canDoNumDimension( numDimensions ) )
			throw new NoSuitablePointsException( model.getClass().getName() + " does not support dim = " + numDimensions );
		
		/* check that number of points is at least model.getMinNumMatches() */
		if ( numNeighbors() < model.getMinNumMatches() )
			throw new NoSuitablePointsException( "At least " + model.getMinNumMatches() + " nearest neighbors are required for a " + model.getClass().getName() + " : num neighbors=" + numNeighbors() );
		
		this.model = model.copy();
	}

	@Override
	public TranslationInvariantModel<?> fitMatches( final ArrayList<PointMatch> matches )
	{
		try
		{
			model.fit( matches );
		}
		catch ( NotEnoughDataPointsException e )
		{
			e.printStackTrace();
		}
		catch ( IllDefinedDataPointsException e )
		{
			e.printStackTrace();
		}		
		
		for ( final PointMatch match : matches )
			match.getP1().apply( model );
		
		return model;
	}

	@Override
	public boolean resetWorldCoordinatesAfterMatching() { return true; }

	@Override
	public boolean useWorldCoordinatesForDescriptorBuildUp() { return false; }
}
