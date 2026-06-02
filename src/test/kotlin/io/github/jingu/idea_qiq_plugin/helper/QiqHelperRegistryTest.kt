package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import com.jetbrains.php.lang.PhpFileType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

/**
 * Unit-tests the static scanner that powers Go to Declaration for Qiq
 * helper calls. The shapes covered here mirror the
 * `QiqHelperLocatorProvider` style used in real-world projects:
 *
 *  - `static function () use (...): ClassName { return new ClassName(...); }`
 *  - `static fn (): ClassName => new ClassName(...)`
 *  - `function () { return new ClassName(...); }` (no declared return)
 *  - `fn () => new ClassName(...)` (no declared return)
 *  - `$x->setFactory(...)` alias (and `register(...)` deliberately ignored)
 *
 * Combined, these patterns cover every `$locator->set(...)` call observed
 * in Hpplus.Spur's `QiqHelperLocatorProvider`.
 */
@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqHelperRegistryTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun extractsDeclaredReturnTypeFromClosure(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Anchor;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator): void
                {
                    ${'$'}locator->set('anchor', static function () use (${'$'}locator): Anchor {
                        return new Anchor(${'$'}locator->get('escape'));
                    });
                }
            }
            """.trimIndent(),
        )
        assertEquals("\\Qiq\\Helper\\Anchor", map["anchor"])
    }

    @Test
    fun extractsDeclaredReturnTypeFromArrowFunction(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\GetLoginUrl;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator): void
                {
                    ${'$'}locator->set('getLoginUrl', static fn (): GetLoginUrl => new GetLoginUrl());
                }
            }
            """.trimIndent(),
        )
        assertEquals("\\Qiq\\Helper\\GetLoginUrl", map["getLoginUrl"])
    }

    @Test
    fun fallsBackToReturnNewExpressionWhenReturnTypeIsMissing(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\TextField;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator): void
                {
                    ${'$'}locator->set('textField', static function () use (${'$'}locator) {
                        return new TextField(${'$'}locator->get('escape'));
                    });
                }
            }
            """.trimIndent(),
        )
        assertEquals("\\Qiq\\Helper\\TextField", map["textField"])
    }

    @Test
    fun fallsBackToArrowBodyNewExpressionWhenReturnTypeIsMissing(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Image;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator): void
                {
                    ${'$'}locator->set('image', static fn () => new Image());
                }
            }
            """.trimIndent(),
        )
        assertEquals("\\Qiq\\Helper\\Image", map["image"])
    }

    @Test
    fun acceptsSetFactoryAliasButNotUnrelatedRegister(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Foo;
            use Qiq\Helper\Bar;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator): void
                {
                    ${'$'}locator->setFactory('bar', static fn (): Bar => new Bar());
                    // `register` is intentionally not a helper-registration
                    // method, so this must NOT be indexed.
                    ${'$'}locator->register('foo', static fn (): Foo => new Foo());
                }
            }
            """.trimIndent(),
        )
        assertEquals("\\Qiq\\Helper\\Bar", map["bar"])
        assertEquals(null, map["foo"], "register(...) must not be indexed, got: $map")
        assertEquals(1, map.size, "Only setFactory should be indexed, got: $map")
    }

    @Test
    fun ignoresRegistrationWithNonStringName(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Foo;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator, string ${'$'}name): void
                {
                    // Dynamic name: nothing to attribute.
                    ${'$'}locator->set(${'$'}name, static fn (): Foo => new Foo());
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, map.size, "Dynamic registrations must not be indexed, got: $map")
    }

    @Test
    fun ignoresRegistrationWithUnresolvableFactory(project: Project) {
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator, ${'$'}svc): void
                {
                    // No declared return type AND the body is not `new X(...)`.
                    ${'$'}locator->set('proxy', static function () use (${'$'}svc) {
                        return ${'$'}svc->make('thing');
                    });
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, map.size, "Indirect factories must not be indexed, got: $map")
    }

    @Test
    fun ignoresNewExpressionNestedInsideReturnedCall(project: Project) {
        // `return foo(new Inner())` returns foo()'s result, not Inner — the
        // scanner must not index 'wrap' as returning Inner.
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Inner;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator, ${'$'}svc): void
                {
                    ${'$'}locator->set('wrap', static function () use (${'$'}svc) {
                        return ${'$'}svc->make(new Inner());
                    });
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, map.size, "new nested in a returned call must not be indexed, got: $map")
    }

    @Test
    fun ignoresNewExpressionThatIsNotTheReturnedValue(project: Project) {
        // A `new` assigned to a local but not returned must not be indexed.
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Tmp;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator, ${'$'}svc): void
                {
                    ${'$'}locator->set('proxy', static function () use (${'$'}svc) {
                        ${'$'}tmp = new Tmp();
                        return ${'$'}svc->make();
                    });
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, map.size, "non-returned new must not be indexed, got: $map")
    }

    @Test
    fun ignoresNewExpressionNestedInsideArrowReturnedCall(project: Project) {
        // `fn () => foo(new Inner())` — Inner is not the arrow's value.
        val map = scan(
            project,
            """
            <?php
            namespace App;

            use Qiq\HelperLocator;
            use Qiq\Helper\Inner;

            class Bootstrap
            {
                public function build(HelperLocator ${'$'}locator, ${'$'}svc): void
                {
                    ${'$'}locator->set('wrap', static fn () => ${'$'}svc->make(new Inner()));
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, map.size, "new nested in an arrow's returned call must not be indexed, got: $map")
    }

    @Test
    fun extractsMultipleRegistrationsFromProviderStyleFile(project: Project) {
        val map = scan(
            project,
            providerStyleFixture(),
        )
        assertEquals("\\Qiq\\Helper\\CardIcon", map["getCardIcon"])
        assertEquals("\\Qiq\\Helper\\ImageFlux", map["imageFlux"])
        assertEquals("\\Qiq\\Helper\\EscapeJs", map["j"])
        assertEquals("\\Qiq\\Helper\\GetLoginUrl", map["getLoginUrl"])
        assertEquals("\\Qiq\\Helper\\GetSignupUrl", map["getSignupUrl"])
        assertEquals(5, map.size, "Unexpected entries: $map")
    }

    // -----------------------------------------------------------------

    private fun scan(project: Project, php: String): Map<String, String> {
        val factory = PsiFileFactory.getInstance(project)
        var psi: PsiFile? = null
        ApplicationManager.getApplication().runReadAction {
            psi = factory.createFileFromText("Bootstrap.php", PhpFileType.INSTANCE, php)
        }
        val file = psi ?: error("Failed to create PHP PsiFile")
        var result: Map<String, String> = emptyMap()
        ApplicationManager.getApplication().runReadAction {
            result = QiqHelperRegistry.getInstance(project).scanForTests(file)
        }
        return result
    }

    private fun providerStyleFixture(): String = """
        <?php
        declare(strict_types=1);

        namespace App\Provider;

        use Qiq\HelperLocator;
        use Qiq\Helper\CardIcon;
        use Qiq\Helper\ImageFlux;
        use Qiq\Helper\EscapeJs;
        use Qiq\Helper\GetLoginUrl;
        use Qiq\Helper\GetSignupUrl;

        class QiqHelperLocatorProvider
        {
            public function get(): HelperLocator
            {
                ${'$'}locator = HelperLocator::new();

                ${'$'}locator->set('getCardIcon', static function () use (${'$'}locator): CardIcon {
                    return new CardIcon(${'$'}locator->get('escape'));
                });

                ${'$'}locator->set('imageFlux', static function () use (${'$'}locator): ImageFlux {
                    return new ImageFlux(${'$'}locator->get('escape'));
                });

                ${'$'}locator->set('j', static function () use (${'$'}locator): EscapeJs {
                    return new EscapeJs(${'$'}locator->get('escape'));
                });

                ${'$'}escape = ${'$'}locator->get('escape');
                ${'$'}locator->set('getLoginUrl', static fn (): GetLoginUrl => new GetLoginUrl(${'$'}escape));
                ${'$'}locator->set('getSignupUrl', static fn (): GetSignupUrl => new GetSignupUrl(${'$'}escape));

                return ${'$'}locator;
            }
        }
        """.trimIndent()
}
