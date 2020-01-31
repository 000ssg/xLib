/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.wamp;

import java.util.Collection;

/**
 * Represents some WAMP constants and owns WAMP role definitions via "smart"
 * enum.
 *
 * WAMP based on https://wamp-proto.org/spec.html
 *
 * @author 000ssg@gmail.com
 */
public interface WAMP {

    public static final String WS_SUB_PROTOCOL_JSON = "wamp.2.json";
    public static final String WS_SUB_PROTOCOL_MESSAGEPACK = "wamp.2.msgpack";

    public static enum Role {
        // top-level
        router,
        client,
        // router sub-roles
        broker,
        dealer,
        // client sub-roles
        caller,
        callee,
        publisher,
        subscriber;

        /**
         * Returns true if list of roles has exact match or if match any
         * sub-role.
         *
         * @param role
         * @param roles
         * @return
         */
        public static boolean hasRole(Role role, Collection<Role> roles) {
            if (role == null || roles == null || roles.isEmpty()) {
                return false;
            } else {
                if (roles.size() == 1) {
                    return hasRole(role, roles.iterator().next());
                } else {
                    return hasRole(role, roles.toArray(new Role[roles.size()]));
                }
            }
        }

        /**
         * Returns true if list of roles has exact match or if match any
         * sub-role.
         *
         * @param role
         * @param roles
         * @return
         */
        public static boolean hasRole(Role role, Role... roles) {
            if (role == null || roles == null || roles.length == 0 || roles.length == 1 && roles[0] == null) {
                return false;
            }
            if (router == role) {
                for (Role r : roles) {
                    if (r == Role.broker) {
                        return true;
                    } else if (r == Role.dealer) {
                        return true;
                    }
                }
            } else if (client == role) {
                for (Role r : roles) {
                    if (r == Role.caller) {
                        return true;
                    } else if (r == Role.callee) {
                        return true;
                    } else if (r == Role.publisher) {
                        return true;
                    } else if (r == Role.subscriber) {
                        return true;
                    }
                }
            } else {
                for (Role r : roles) {
                    if (role == r) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Returns true if list of roles has exact match or if match any
         * sub-role for any of roles in anyRole parameter..
         *
         * @param role
         * @param roles
         * @return
         */
        public static boolean hasEitherRole(Collection<Role> anyRole, Role... roles) {
            if (anyRole == null || anyRole.isEmpty() || roles == null || roles.length == 0 || roles.length == 1 && roles[0] == null) {
                return false;
            }
            return hasEitherRole(anyRole.toArray(new Role[anyRole.size()]), roles);
        }

        /**
         * Returns true if list of roles has exact match or if match any
         * sub-role for any of roles in anyRole parameter..
         *
         * @param role
         * @param roles
         * @return
         */
        public static boolean hasEitherRole(Collection<Role> anyRole, Collection<Role> roles) {
            if (anyRole == null || anyRole.isEmpty() || roles == null || roles.isEmpty()) {
                return false;
            }
            return hasEitherRole(anyRole.toArray(new Role[anyRole.size()]), roles.toArray(new Role[roles.size()]));
        }

        /**
         * Returns true if list of roles has exact match or if match any
         * sub-role for any of roles in anyRole parameter..
         *
         * @param role
         * @param roles
         * @return
         */
        public static boolean hasEitherRole(Role[] anyRole, Role... roles) {
            if (anyRole == null || roles == null || roles.length == 0 || roles.length == 1 && roles[0] == null) {
                return false;
            }
            for (Role role : anyRole) {
                if (router == role) {
                    for (Role r : roles) {
                        if (r == Role.broker) {
                            return true;
                        } else if (r == Role.dealer) {
                            return true;
                        }
                    }
                } else if (client == role) {
                    for (Role r : roles) {
                        if (r == Role.caller) {
                            return true;
                        } else if (r == Role.callee) {
                            return true;
                        } else if (r == Role.publisher) {
                            return true;
                        } else if (r == Role.subscriber) {
                            return true;
                        }
                    }
                } else {
                    for (Role r : roles) {
                        if (role == r) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

}
