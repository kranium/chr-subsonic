/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.jmeplayer.screens;

import net.sourceforge.subsonic.jmeplayer.domain.MusicDirectory;
import net.sourceforge.subsonic.jmeplayer.service.MusicService;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * @author Sindre Mehus
 */
public class MusicDirectoryScreen extends List {

    private MusicDirectory musicDirectory;
    private final MusicService musicService;
    private final Display display;
    private ArtistScreen artistScreen;
    private PlayerScreen playerScreen;

    public MusicDirectoryScreen(final MusicService musicService, final Display display) {
        super("Select", IMPLICIT);
        this.musicService = musicService;
        this.display = display;

        final Command selectCommand = new Command("Select", Command.ITEM, 1);
        final Command backCommand = new Command("Back", Command.BACK, 2);

        addCommand(selectCommand);
        addCommand(backCommand);
        setSelectCommand(selectCommand);

        setCommandListener(new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backCommand) {
                    display.setCurrent(artistScreen); // TODO: Should rather show parent.
                } else {
                    MusicDirectory.Entry[] entries = getSelectedEntries();
                    if (entries.length == 1 && entries[0].isDirectory()) {
                        setPath(entries[0].getPath());
                    } else {
                        playerScreen.setMusicDirectoryEntries(musicDirectory, entries);
                        display.setCurrent(playerScreen);
                    }
                }
            }
        });
    }

    public void setPath(final String path) {
        new Worker(display, MusicDirectoryScreen.this, "Contacting server...") {
            protected Object doInBackground() throws Throwable {
                return musicService.getMusicDirectory(path);
            }

            protected void done(Object result) {
                setMusicDirectory((MusicDirectory) result);
            }


            protected void interrupt() throws Exception {
                musicService.interrupt();
            }
        }.start();
    }

    private void setMusicDirectory(MusicDirectory musicDirectory) {
        this.musicDirectory = musicDirectory;
        setTitle(musicDirectory.getName());
        deleteAll();
        append("[Play all]", null);
        for (int i = 0; i < musicDirectory.getChildren().length; i++) {
            MusicDirectory.Entry entry = musicDirectory.getChildren()[i];
            append(entry.getName(), null);
        }
    }

    private MusicDirectory.Entry[] getSelectedEntries() {
        int index = getSelectedIndex();
        if (index == 0) {
            return musicDirectory.getChildren(false);
        }
        return new MusicDirectory.Entry[]{musicDirectory.getChildren()[getSelectedIndex() - 1]};
    }

    public void setArtistScreen(ArtistScreen artistScreen) {
        this.artistScreen = artistScreen;
    }

    public void setPlayerScreen(PlayerScreen playerScreen) {
        this.playerScreen = playerScreen;
    }

}