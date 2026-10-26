package pro.sketchware.ai.project

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkProjectReconstructorTest {

    @Test
    fun adaptDecodedXmlForSketchware_mapsConstraintAndMaterialViewsToSketchwareWidgets() {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/cardHero"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardCornerRadius="16dp">
                    <androidx.appcompat.widget.AppCompatTextView
                        android:id="@+id/tvTitle"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Welcome Clone"
                        android:textColor="@color/primary_text" />
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnAction"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Login" />
                </com.google.android.material.card.MaterialCardView>
                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rvItems"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val adapted = ApkProjectReconstructor.adaptDecodedXmlForSketchware(raw, setOf("ic_logo"))

        assertTrue(adapted.contains("<LinearLayout"))
        assertTrue(adapted.contains("<TextView"))
        assertTrue(adapted.contains("<Button"))
        assertTrue(adapted.contains("<ListView"))
        assertTrue(adapted.contains("android:id=\"@+id/tvtitle\""))
        assertTrue(adapted.contains("android:id=\"@+id/btnaction\""))
        assertTrue(adapted.contains("android:id=\"@+id/rvitems\""))
        assertFalse(adapted.contains("ConstraintLayout"))
        assertFalse(adapted.contains("MaterialCardView"))
        assertFalse(adapted.contains("RecyclerView"))
        assertFalse(adapted.contains("@color/primary_text"))
    }
}
