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
package net.sourceforge.subsonic.controller;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.multiaction.MultiActionController;

import net.sourceforge.subsonic.domain.MusicFile;
import net.sourceforge.subsonic.domain.MusicFolder;
import net.sourceforge.subsonic.domain.MusicIndex;
import net.sourceforge.subsonic.domain.Player;
import net.sourceforge.subsonic.domain.Playlist;
import net.sourceforge.subsonic.domain.RandomSearchCriteria;
import net.sourceforge.subsonic.domain.User;
import net.sourceforge.subsonic.domain.SearchCriteria;
import net.sourceforge.subsonic.domain.SearchResult;
import net.sourceforge.subsonic.service.MusicFileService;
import net.sourceforge.subsonic.service.MusicIndexService;
import net.sourceforge.subsonic.service.PlayerService;
import net.sourceforge.subsonic.service.PlaylistService;
import net.sourceforge.subsonic.service.SearchService;
import net.sourceforge.subsonic.service.SecurityService;
import net.sourceforge.subsonic.service.SettingsService;
import net.sourceforge.subsonic.service.VersionService;
import net.sourceforge.subsonic.util.StringUtil;

/**
 * Multi-controller used for wap pages.
 *
 * @author Sindre Mehus
 */
public class WapController extends MultiActionController {

    private SettingsService settingsService;
    private PlayerService playerService;
    private PlaylistService playlistService;
    private SearchService searchService;
    private SecurityService securityService;
    private MusicFileService musicFileService;
    private MusicIndexService musicIndexService;
    private VersionService versionService;

    public ModelAndView index(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return wap(request, response);
    }

    public ModelAndView wap(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        List<MusicFolder> folders = settingsService.getAllMusicFolders();

        if (folders.isEmpty()) {
            map.put("noMusic", true);
        } else {

            SortedMap<MusicIndex, SortedSet<MusicIndex.Artist>> allArtists = musicIndexService.getIndexedArtists(folders);

            // If an index is given as parameter, only show music files for this index.
            String index = request.getParameter("index");
            if (index != null) {
                SortedSet<MusicIndex.Artist> artists = allArtists.get(new MusicIndex(index));
                if (artists == null) {
                    map.put("noMusic", true);
                } else {
                    map.put("artists", artists);
                }
            }

            // Otherwise, list all indexes.
            else {
                map.put("indexes", allArtists.keySet());
            }
        }

        return new ModelAndView("wap/index", "model", map);
    }

    public ModelAndView browse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = request.getParameter("path");
        MusicFile parent = musicFileService.getMusicFile(path);

        // Create array of file(s) to display.
        List<MusicFile> children;
        if (parent.isDirectory()) {
            children = parent.getChildren(true, true);
        } else {
            children = new ArrayList<MusicFile>();
            children.add(parent);
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("parent", parent);
        map.put("children", children);
        map.put("user", securityService.getCurrentUser(request));

        return new ModelAndView("wap/browse", "model", map);
    }

    public ModelAndView playlist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // Create array of players to control. If the "player" attribute is set for this session,
        // only the player with this ID is controlled.  Otherwise, all players are controlled.
        List<Player> players = playerService.getAllPlayers();

        String playerId = (String) request.getSession().getAttribute("player");
        if (playerId != null) {
            Player player = playerService.getPlayerById(playerId);
            if (player != null) {
                players = Arrays.asList(player);
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();

        for (Player player : players) {
            Playlist playlist = player.getPlaylist();
            map.put("playlist", playlist);

            if (request.getParameter("play") != null) {
                MusicFile file = musicFileService.getMusicFile(request.getParameter("play"));
                playlist.addFiles(false, file);
            } else if (request.getParameter("add") != null) {
                MusicFile file = musicFileService.getMusicFile(request.getParameter("add"));
                playlist.addFiles(true, file);
            } else if (request.getParameter("skip") != null) {
                playlist.setIndex(Integer.parseInt(request.getParameter("skip")));
            } else if (request.getParameter("clear") != null) {
                playlist.clear();
            } else if (request.getParameter("load") != null) {
                playlistService.loadPlaylist(playlist, request.getParameter("load"));
            } else if (request.getParameter("random") != null) {
                List<MusicFile> randomFiles = searchService.getRandomSongs(new RandomSearchCriteria(20, null, null, null, null));
                playlist.addFiles(false, randomFiles);
            }
        }

        map.put("players", players);
        return new ModelAndView("wap/playlist", "model", map);
    }

    public ModelAndView loadPlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("playlists", playlistService.getSavedPlaylists());
        return new ModelAndView("wap/loadPlaylist", "model", map);
    }

    public ModelAndView search(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return new ModelAndView("wap/search");
    }

    public ModelAndView searchResult(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String query = request.getParameter("query");
        boolean creatingIndex = searchService.isIndexBeingCreated();

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("creatingIndex", creatingIndex);

        if (!creatingIndex) {
            map.put("hits", search(query));
        }

        return new ModelAndView("wap/searchResult", "model", map);
    }

    public ModelAndView settings(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String playerId = (String) request.getSession().getAttribute("player");

        List<Player> allPlayers = playerService.getAllPlayers();
        User user = securityService.getCurrentUser(request);
        List<Player> players = new ArrayList<Player>();
        Map<String, Object> map = new HashMap<String, Object>();

        for (Player player : allPlayers) {
            // Only display authorized players.
            if (user.isAdminRole() || user.getUsername().equals(player.getUsername())) {
                players.add(player);
            }

        }
        map.put("playerId", playerId);
        map.put("players", players);
        return new ModelAndView("wap/settings", "model", map);
    }

    public ModelAndView selectPlayer(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.getSession().setAttribute("player", request.getParameter("playerId"));
        return settings(request, response);
    }

    public ModelAndView playerJad(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();

        // Note: The MIDP specification requires that the version is of the form X.Y[.Z], where X, Y, Z are
        // integers between 0 and 99.
        String version = versionService.getLocalVersion().toString().replaceAll("\\.beta.*", "");

        map.put("baseUrl", getBaseUrl(request));
        map.put("jarSize", getJarSize());
        map.put("version", version);
        return new ModelAndView("mobile/playerJad", "model", map);
    }

    public ModelAndView playerJar(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("application/java-archive");
        response.setContentLength(getJarSize());
        InputStream in = getJarInputStream();
        try {
            IOUtils.copy(in, response.getOutputStream());
        } finally {
            IOUtils.closeQuietly(in);
        }
        return null;
    }

    private List<MusicFile> search(String query) throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setTitle(query);
        criteria.setOffset(0);
        criteria.setCount(50);

        SearchResult result = searchService.search(criteria);
        return result.getMusicFiles();
    }

    private int getJarSize() throws Exception {
        InputStream in = getJarInputStream();
        try {
            return IOUtils.toByteArray(in).length;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String baseUrl = request.getRequestURL().toString();
        baseUrl = baseUrl.replaceFirst("/wap.*", "/");

        // Rewrite URLs in case we're behind a proxy.
        if (settingsService.isRewriteUrlEnabled()) {
            String referer = request.getHeader("referer");
            baseUrl = StringUtil.rewriteUrl(baseUrl, referer);
        }
        return baseUrl;
    }

    private InputStream getJarInputStream() {
        return getClass().getResourceAsStream("subsonic-jme-player.jar");
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setPlaylistService(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setMusicFileService(MusicFileService musicFileService) {
        this.musicFileService = musicFileService;
    }

    public void setMusicIndexService(MusicIndexService musicIndexService) {
        this.musicIndexService = musicIndexService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }
}
