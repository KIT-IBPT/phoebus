/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.display.builder.runtime.app;

import java.net.URI;
import java.util.List;

import org.csstudio.display.builder.model.DisplayModel;
import org.phoebus.framework.spi.AppResourceDescriptor;
import org.phoebus.ui.docking.DockItemWithInput;
import org.phoebus.ui.docking.DockStage;

/** Display Runtime Application
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class DisplayRuntimeApplication implements AppResourceDescriptor
{
    public static final String NAME = "display_runtime";
    public static final String DISPLAY_NAME = "Display Runtime";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDisplayName()
    {
        return DISPLAY_NAME;
    }

    @Override
    public List<String> supportedFileExtentions()
    {
        return List.of(DisplayModel.FILE_EXTENSION, DisplayModel.LEGACY_FILE_EXTENSION);
    }

    @Override
    public DisplayRuntimeInstance create()
    {
        return new DisplayRuntimeInstance(this);
    }

    @Override
    public DisplayRuntimeInstance create(final URI resource)
    {
        // Create display info
        final DisplayInfo info = DisplayInfo.forURI(resource);

        // Convert back into URI
        // Content should be very similar, but normalized such that for example macros
        // are alphabetically sorted to uniquely identify an already running instance
        // via its input
        final URI input = info.toURI();

        // Check for existing instance with that input, i.e. path & macros
        final DisplayRuntimeInstance instance;
        final DockItemWithInput existing = DockStage.getDockItemWithInput(NAME, input);
        if (existing != null)
        {   // Found one, raise it
            instance = existing.getApplication();
            instance.raise();
        }
        else
        {   // Nothing found, create new one
            instance = create();
            instance.loadDisplayFile(info);
        }

        return instance;
    }
}