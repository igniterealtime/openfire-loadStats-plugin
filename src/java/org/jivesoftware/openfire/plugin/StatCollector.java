/*
 * Copyright (C) 2005-2008 Jive Software, 2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.database.ConnectionProvider;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.DefaultConnectionProvider;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

/**
 * Collector of raw data that is print to a log file every minute.
 *
 * @author Gaston Dombiak
 */
public class StatCollector extends TimerTask {

    private static final Logger Log = LoggerFactory.getLogger(StatCollector.class);

    private static DefaultConnectionProvider getDefaultConnectionProvider() {
        final ConnectionProvider connectionProvider = DbConnectionManager.getConnectionProvider();
        if(connectionProvider instanceof DefaultConnectionProvider) {
            return (DefaultConnectionProvider) connectionProvider;
        } else {
            return null;
        }
    }

    private boolean headerPrinter = false;
    private List<String> content = new ArrayList<>();
    // Take a sample every X seconds
    private int frequency;
    private boolean started = false;

    public StatCollector(int frequency) {
        this.frequency = frequency;
    }

    @Override
    public void run() {
        try {
            // Collect content
            StringBuilder sb = new StringBuilder();
            // Add current timestamp
            sb.append(System.currentTimeMillis());
            sb.append(',');
            // Add info about the db connection pool
            sb.append(getMinimumConnectionCount());
            sb.append(',');
            sb.append(getMaximumConnectionCount());
            sb.append(',');
            sb.append(getActiveConnectionCount());
            sb.append(',');
            sb.append(getServedCount());
            // Add info about number of connected sessions
            sb.append(',');
            sb.append(SessionManager.getInstance().getConnectionsCount(false));

            // Add new line of content with current stats
            content.add(sb.toString());

            // Check if we need to print content to file (print content every minute)
            if (content.size() > (60f / frequency * 1000)) {
                try {
                    File file = JiveGlobals.getHomePath().resolve("logs").resolve(JiveGlobals.getProperty("statistic.filename", "stats.txt")).toFile();
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    BufferedWriter out = new BufferedWriter(new FileWriter(file, true));
                    if (!headerPrinter) {
                        out.write(new Date().toString());
                        out.write('\n');
                        out.write(
                                "Timestamp, DB min, DB max, DB current, DB used, Core Threads, Active Threads, Queue Tasks, Completed Tasks, Sessions, NIO Read, NIO Written, Queued NIO events, Queues NIO writes");
                        out.write('\n');
                        headerPrinter = true;
                    }
                    for (String line : content) {
                        out.write(line);
                        out.write('\n');
                    }
                    out.close();
                } catch (IOException e) {
                    Log.error("Error creating statistics log file", e);
                }
                content.clear();
            }
        } catch (Exception e) {
            Log.error("Error collecting and logging server statistics", e);
        }
    }

    public synchronized void start() {
        if (!started) {
            started = true;
            TaskEngine.getInstance().scheduleAtFixedRate(this, Duration.ofMillis(1000), Duration.ofMillis(frequency));
        }
    }

    public void stop() {
        if (started) {
            TaskEngine.getInstance().cancelScheduledTask(this);
        }
    }

    public int getActiveConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getActiveConnections();
    }

    public long getServedCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getConnectionsServed();
    }

    public int getMaximumConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getMaxConnections();
    }

    public int getMinimumConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getMinConnections();
    }
}
