/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.event.impl.jobs.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.sling.event.impl.jobs.JobEvent;
import org.apache.sling.event.jobs.JobUtil;
import org.osgi.service.event.Event;

public class InternalQueueConfigurationTest {

    private JobEvent getJobEvent(final String topic) {
        final Dictionary<String, Object> dict = new Hashtable<String, Object>();
        dict.put(JobUtil.PROPERTY_JOB_TOPIC, topic);
        return new JobEvent(new Event(topic, dict), topic) {
            public void unlock() {
                // dummy
            }
            public boolean reschedule() {
                return false;
            }
            public boolean remove() {
                return false;
            }
            public boolean lock() {
                return false;
            }
            public void finished() {
                // dummy
            }
            public void restart() {
                // dummy
            }
            public boolean isAlive() { return false; }
        };
    }
    @org.junit.Test public void testMaxParallel() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_MAX_PARALLEL, -1);

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertEquals(Runtime.getRuntime().availableProcessors(), c.getMaxParallel());
    }

    @org.junit.Test public void testTopicMatchersDot() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a."});
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        assertTrue(c.match(getJobEvent("a/b")));
        assertTrue(c.match(getJobEvent("a/c")));
        assertFalse(c.match(getJobEvent("a")));
        assertFalse(c.match(getJobEvent("a/b/c")));
        assertFalse(c.match(getJobEvent("t")));
        assertFalse(c.match(getJobEvent("t/x")));
    }

    @org.junit.Test public void testTopicMatchersStar() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a*"});
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        assertTrue(c.match(getJobEvent("a/b")));
        assertTrue(c.match(getJobEvent("a/c")));
        assertFalse(c.match(getJobEvent("a")));
        assertTrue(c.match(getJobEvent("a/b/c")));
        assertFalse(c.match(getJobEvent("t")));
        assertFalse(c.match(getJobEvent("t/x")));
    }

    @org.junit.Test public void testTopicMatchers() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a"});
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        assertFalse(c.match(getJobEvent("a/b")));
        assertFalse(c.match(getJobEvent("a/c")));
        assertTrue(c.match(getJobEvent("a")));
        assertFalse(c.match(getJobEvent("a/b/c")));
        assertFalse(c.match(getJobEvent("t")));
        assertFalse(c.match(getJobEvent("t/x")));
    }

    @org.junit.Test public void testTopicMatcherAndReplacement() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a."});
        p.put(ConfigurationConstants.PROP_NAME, "test-queue-{0}");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        final JobEvent b = getJobEvent("a/b");
        assertTrue(c.match(b));
        assertEquals("test-queue-b", b.queueName);
        final JobEvent d = getJobEvent("a/d");
        assertTrue(c.match(d));
        assertEquals("test-queue-d", d.queueName);
    }

    @org.junit.Test public void testTopicMatchersDotAndSlash() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a/."});
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        assertTrue(c.match(getJobEvent("a/b")));
        assertTrue(c.match(getJobEvent("a/c")));
        assertFalse(c.match(getJobEvent("a")));
        assertFalse(c.match(getJobEvent("a/b/c")));
        assertFalse(c.match(getJobEvent("t")));
        assertFalse(c.match(getJobEvent("t/x")));
    }

    @org.junit.Test public void testTopicMatchersStarAndSlash() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a/*"});
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        assertTrue(c.match(getJobEvent("a/b")));
        assertTrue(c.match(getJobEvent("a/c")));
        assertFalse(c.match(getJobEvent("a")));
        assertTrue(c.match(getJobEvent("a/b/c")));
        assertFalse(c.match(getJobEvent("t")));
        assertFalse(c.match(getJobEvent("t/x")));
    }

    @org.junit.Test public void testTopicMatcherAndReplacementAndSlash() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_TOPICS, new String[] {"a/."});
        p.put(ConfigurationConstants.PROP_NAME, "test-queue-{0}");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertTrue(c.isValid());
        final JobEvent b = getJobEvent("a/b");
        assertTrue(c.match(b));
        assertEquals("test-queue-b", b.queueName);
        final JobEvent d = getJobEvent("a/d");
        assertTrue(c.match(d));
        assertEquals("test-queue-d", d.queueName);
    }

    @org.junit.Test public void testNoTopicMatchers() {
        final Map<String, Object> p = new HashMap<String, Object>();
        p.put(ConfigurationConstants.PROP_NAME, "test");

        InternalQueueConfiguration c = InternalQueueConfiguration.fromConfiguration(p);
        assertFalse(c.isValid());
    }
}
