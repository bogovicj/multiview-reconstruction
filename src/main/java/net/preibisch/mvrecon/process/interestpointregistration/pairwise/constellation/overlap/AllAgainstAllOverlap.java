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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap;

import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;

public class AllAgainstAllOverlap< V > implements OverlapDetection< V >
{
	final int n;

	public AllAgainstAllOverlap( final int numDimensions ){ this.n = numDimensions; }

	@Override
	public boolean overlaps( final V view1, final V view2 ) { return true; }

	@Override
	public RealInterval getOverlapInterval( final V view1, final V view2 )
	{
		final double[] min = new double[ n ];
		final double[] max = new double[ n ];

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = 0;
			max[ d ] = 1;
		}

		return new FinalRealInterval( min, max );
	}
}
