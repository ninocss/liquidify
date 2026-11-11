package dev.nino.squareglass;

public final class ArtworkChecks {
    public static void main(String[] args) {
        // Exhaust saturated, dark and bright covers against the host's real text ink.
        for (boolean dark : new boolean[]{false,true}) {
            int ink=dark ? 0xffffffff : 0xff15161a;
            for (int r=0;r<256;r+=17) for (int g=0;g<256;g+=17) for (int b=0;b<256;b+=17) {
                int surface=CoverColors.surface(0xff000000|r<<16|g<<8|b,dark);
                if (CoverColors.contrast(surface,ink)<4.5) throw new AssertionError("Unreadable artwork tone");
            }
        }
        for (int r=0;r<256;r+=17) for (int g=0;g<256;g+=17) for (int b=0;b<256;b+=17) {
            int surface=CoverColors.playerSurface(0xff000000|r<<16|g<<8|b, true);
            if (CoverColors.contrast(surface,0xffffffff)<5.5) throw new AssertionError("Unreadable portrait player surface");
        }
        for (int r=0;r<256;r+=17) for (int g=0;g<256;g+=17) for (int b=0;b<256;b+=17) {
            int surface=CoverColors.playerSurface(0xff000000|r<<16|g<<8|b, false);
            if (CoverColors.contrast(surface,0xff15161a)<4.5) throw new AssertionError("Unreadable light portrait player surface");
        }
        int pink=CoverColors.surface(0xffd976cc,false), blue=CoverColors.surface(0xff3788cf,false);
        if (((pink>>>16)&255)<=((pink>>>8)&255) || (blue&255)<=((blue>>>16)&255))
            throw new AssertionError("Artwork hue lost");
        if (CoverColors.seed(new int[]{0x00ff0000,0xff00ff00})!=0xff00ff00)
            throw new AssertionError("Transparent pixels influence palette");
        if (CoverColors.seed(new int[]{0,0})!=0xff808080) throw new AssertionError("Missing artwork fallback");
        int dominant=CoverColors.seed(new int[]{0xff348f99,0xff368d99,0xff358d98,0xff368e98,0xff000000,0xff000000,0xffffd700});
        if ((dominant&255)<=((dominant>>>16)&255)) throw new AssertionError("Small bright accent dominates the cover background");
        System.out.println("16384 artwork contrast cases and palette checks passed");
    }
}
