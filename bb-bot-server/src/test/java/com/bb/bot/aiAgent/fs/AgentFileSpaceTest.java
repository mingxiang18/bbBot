package com.bb.bot.aiAgent.fs;

import com.bb.bot.aiAgent.tools.MemoryToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFileSpaceTest {

    @TempDir
    Path tmp;

    private AgentFileSpace space;

    @BeforeEach
    void setUp() {
        space = new AgentFileSpace();
        ReflectionTestUtils.setField(space, "userFileRoot", tmp.toString());
    }

    @Test
    void userRoot_isUserFileRootSlashSafeUserId() {
        assertEquals(tmp.resolve("user-123"), space.userRoot("user-123"));
    }

    @Test
    void safe_keepsWordAndHyphen_replacesEverythingElse() {
        assertEquals("a_b_c", AgentFileSpace.safe("a/b.c"));
        assertEquals("user-123", AgentFileSpace.safe("user-123"));
        // userId 里的目录穿越被中和
        assertEquals("___etc", AgentFileSpace.safe("../etc"));
    }

    @Test
    void safe_nullOrEmpty_becomesAnonymous() {
        assertEquals("_anonymous", AgentFileSpace.safe(null));
        assertEquals("_anonymous", AgentFileSpace.safe(""));
    }

    @Test
    void resolveWithin_relativePath_resolvesUnderUserRoot() {
        Path resolved = space.resolveWithin("u1", "inbound/m1/a.txt");
        assertEquals(tmp.resolve("u1").resolve("inbound").resolve("m1").resolve("a.txt"), resolved);
    }

    @Test
    void resolveWithin_emptyPath_isUserRoot() {
        assertEquals(space.userRoot("u1"), space.resolveWithin("u1", ""));
        assertEquals(space.userRoot("u1"), space.resolveWithin("u1", null));
    }

    @Test
    void resolveWithin_absolutePathInsideUserRoot_isAccepted() {
        Path inside = space.userRoot("u1").resolve("x.txt");
        assertEquals(inside, space.resolveWithin("u1", inside.toString()));
    }

    @Test
    void resolveWithin_dotDotEscape_isRejected() {
        assertThrows(AgentFileSpace.PathEscapeException.class,
                () -> space.resolveWithin("u1", "../u2/secret.txt"));
    }

    @Test
    void resolveWithin_absolutePathOfAnotherUser_isRejected() {
        // 防越权：u1 不能借绝对路径读到 u2 的目录
        Path otherUserFile = space.userRoot("u2").resolve("secret.txt");
        assertThrows(AgentFileSpace.PathEscapeException.class,
                () -> space.resolveWithin("u1", otherUserFile.toString()));
    }

    @Test
    void resolveWithin_absolutePathOutsideRoot_isRejected() {
        assertThrows(AgentFileSpace.PathEscapeException.class,
                () -> space.resolveWithin("u1", "/etc/passwd"));
    }

    @Test
    void resolveForCurrentUser_usesThreadLocalCallerId() {
        MemoryToolContext.setUserId("u9");
        try {
            assertEquals(tmp.resolve("u9").resolve("f.txt"), space.resolveForCurrentUser("f.txt"));
        } finally {
            MemoryToolContext.clear();
        }
    }

    @Test
    void resolveForCurrentUser_caller1CannotReachCaller2Dir() {
        MemoryToolContext.setUserId("caller1");
        try {
            Path caller2File = space.userRoot("caller2").resolve("private.txt");
            assertThrows(AgentFileSpace.PathEscapeException.class,
                    () -> space.resolveForCurrentUser(caller2File.toString()));
        } finally {
            MemoryToolContext.clear();
        }
    }

    @Test
    void resolveWithin_resultAlwaysUnderUserRoot() {
        Path root = space.userRoot("u1");
        assertTrue(space.resolveWithin("u1", "a/b/c.txt").startsWith(root));
    }
}
