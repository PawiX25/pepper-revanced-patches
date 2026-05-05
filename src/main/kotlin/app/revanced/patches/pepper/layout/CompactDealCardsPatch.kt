package app.revanced.patches.pepper.layout

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.stringOption
import app.revanced.patches.pepper.shared.pepperFamilyPackages
import com.android.tools.smali.dexlib2.Opcode

/**
 * Make redesigned deal-list cards closer to the pre-redesign compact density.
 *
 * Pepper 7.19.00 shipped a compact deal card with a 92dp thumbnail and 32dp
 * temperature controls. Pepper 8.13.00 moved to a taller card: 108dp thumbnail,
 * 36dp voting controls, a larger left column and Material3 buttons. ReVanced's
 * resource patch path is intentionally avoided here because this project has
 * already verified that no-op resource recompilation can break Pepper 8.13 boot.
 *
 * Instead, this patch hooks the deal-card ViewHolder constructor after all
 * findViewById calls and adjusts runtime LayoutParams/min heights.
 */
@Suppress("unused")
val compactDealCardsPatch = bytecodePatch(
    name = "Compact deal cards (bytecode fallback)",
    description = "Shrinks Pepper 8.x deal-list cards toward the denser " +
        "pre-redesign layout without recompiling resources.",
    use = false,
) {
    pepperFamilyPackages.forEach { compatibleWith(it) }

    val spacingUnitDimenIdOpt by stringOption(
        key = "spacingUnitDimenId",
        default = "0x7f070737",
        title = "spacing_extra_small dimen R.id (hex)",
        description = "Resource ID of `spacing_extra_small` (4dp). Default " +
            "matches Pepper PL v8.13.00 and is used as the density-aware unit " +
            "for 92dp, 40dp and 32dp runtime sizes.",
        required = true,
    )

    execute {
        fun parseHex(s: String): Int =
            (if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2) else s)
                .toLong(16).toInt()

        val spacingUnitDimenId = parseHex(spacingUnitDimenIdOpt!!)
        val constructor = dealCardViewHolderConstructorFingerprint.method
        val implementation = constructor.implementation
            ?: throw PatchException("Deal-card ViewHolder constructor has no implementation")
        val instructions = implementation.instructions.toList()
        val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (returnIndex < 0) {
            throw PatchException("Deal-card ViewHolder constructor has no return-void")
        }

        val holderClass = constructor.definingClass.toString()

        constructor.addInstructions(
            returnIndex,
            """
            iget-object v0, p0, ${holderClass}->J:Landroid/widget/TextView;
            const/4 v1, 0x3
            invoke-virtual { v0, v1 }, Landroid/widget/TextView;->setMaxLines(I)V

            iget-object v0, p0, ${holderClass}->K:Landroid/widget/ImageView;
            invoke-virtual { v0 }, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object v1
            const v2, $spacingUnitDimenId
            invoke-virtual { v1, v2 }, Landroid/content/res/Resources;->getDimensionPixelSize(I)I
            move-result v2
            mul-int/lit8 v2, v2, 0x17
            invoke-virtual { v0 }, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
            move-result-object v1
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->width:I
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
            invoke-virtual { v0, v1 }, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

            iget-object v0, p0, ${holderClass}->L:Landroid/widget/ImageButton;
            invoke-virtual { v0 }, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object v1
            const v2, $spacingUnitDimenId
            invoke-virtual { v1, v2 }, Landroid/content/res/Resources;->getDimensionPixelSize(I)I
            move-result v2
            mul-int/lit8 v2, v2, 0x8
            invoke-virtual { v0 }, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
            move-result-object v1
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->width:I
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
            invoke-virtual { v0, v1 }, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

            iget-object v0, p0, ${holderClass}->N:Landroid/widget/ImageButton;
            invoke-virtual { v0 }, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
            move-result-object v1
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->width:I
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
            invoke-virtual { v0, v1 }, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

            iget-object v0, p0, ${holderClass}->M:Landroid/widget/TextView;
            invoke-virtual { v0, v2 }, Landroid/widget/TextView;->setMinHeight(I)V

            iget-object v0, p0, ${holderClass}->F:Landroid/widget/ImageButton;
            invoke-virtual { v0 }, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object v1
            const v2, $spacingUnitDimenId
            invoke-virtual { v1, v2 }, Landroid/content/res/Resources;->getDimensionPixelSize(I)I
            move-result v2
            mul-int/lit8 v2, v2, 0xa
            invoke-virtual { v0 }, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
            move-result-object v1
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->width:I
            iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
            invoke-virtual { v0, v1 }, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

            iget-object v0, p0, ${holderClass}->X:Lcom/google/android/material/button/MaterialButton;
            invoke-virtual { v0, v2 }, Landroid/view/View;->setMinimumHeight(I)V
            invoke-virtual { v0, v2 }, Landroid/widget/TextView;->setMinHeight(I)V

            iget-object v0, p0, ${holderClass}->Y:Lcom/google/android/material/button/MaterialButton;
            invoke-virtual { v0, v2 }, Landroid/view/View;->setMinimumHeight(I)V
            invoke-virtual { v0, v2 }, Landroid/widget/TextView;->setMinHeight(I)V

            iget-object v0, p0, ${holderClass}->Z:Lcom/google/android/material/button/MaterialButton;
            invoke-virtual { v0, v2 }, Landroid/view/View;->setMinimumHeight(I)V
            invoke-virtual { v0, v2 }, Landroid/widget/TextView;->setMinHeight(I)V
            """.trimIndent(),
        )
    }
}
