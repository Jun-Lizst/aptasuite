/**
 * 
 */
package lib.structure.rnafold;

import exceptions.InvalidConfigurationException;

/**
 * @author Jan Hoinka
 *
 * RNAFold4j is a port of RNAFold implemented and developed by Ivo L Hofacker et al.
 * as part of the ViennaRNA package v 1.8.5. All intellectual credits of this work 
 * go to the original authors and the Institute for Theoretical Chemistry of the 
 * University of Vienna. My only contribution is the adaptation of the C source to Java.
 */
public final class PairMat {

	static int NBASES = 8;

	static String Law_and_Order = "_ACGUTXKI";
	static int[][] BP_pair =
				   /* _  A  C  G  U  X  K  I */
			{ 		{ 0, 0, 0, 0, 0, 0, 0, 0 }, 
					{ 0, 0, 0, 0, 5, 0, 0, 5 }, 
					{ 0, 0, 0, 1, 0, 0, 0, 0 },
					{ 0, 0, 2, 0, 3, 0, 0, 0 }, 
					{ 0, 6, 0, 4, 0, 0, 0, 6 }, 
					{ 0, 0, 0, 0, 0, 0, 2, 0 },
					{ 0, 0, 0, 0, 0, 1, 0, 0 }, 
					{ 0, 6, 0, 0, 5, 0, 0, 0 } };

	static int MAXALPHA = 20; /* maximal length of alphabet */

	static short[] alias = new short[MAXALPHA + 1];
	static int[][] pair = new int[MAXALPHA + 1][MAXALPHA + 1];
	/* rtype[pair[i][j]]:=pair[j][i] */
	static int[] rtype = { 0, 2, 1, 4, 3, 6, 5, 7 };

	/* for backward compatibility */
	// #define ENCODE(c) encode_char(c)

	static int encode_char(byte c) {
		/* return numerical representation of base used e.g. in pair[][] */
		int code;
		if (FoldVars.energy_set > 0)
			code = (int) (c - 'A') + 1;
		else {
			int pos = Law_and_Order.indexOf(c);
			if (pos == -1)
				code = 0;
			else
				code = pos;
			if (code > 4)
				code--; /* make T and U equivalent */
		}
		return code;
	}

	/* @+boolint +charint@ */
	/* @null@ */
	// extern char *nonstandards;
	// extern void nrerror(const char message[]);
	static void make_pair_matrix() {
		int i, j;

		if (FoldVars.energy_set == 0) {
			for (i = 0; i < 5; i++)
				alias[i] = (short) i;
			alias[5] = 3; /* X <-> G */
			alias[6] = 2; /* K <-> C */
			alias[7] = 0; /* I <-> default base '@' */
			for (i = 0; i < NBASES; i++) {
				for (j = 0; j < NBASES; j++)
					pair[i][j] = BP_pair[i][j];
			}
			if (FoldVars.noGU)
				pair[3][4] = pair[4][3] = 0;
			if (FoldVars.nonstandards != null) { /* allow nonstandard bp's */
				for (i = 0; i < FoldVars.nonstandards.length; i += 2)
					pair[encode_char(FoldVars.nonstandards[i])][encode_char(FoldVars.nonstandards[i + 1])] = 7;
			}
			for (i = 0; i < NBASES; i++) {
				for (j = 0; j < NBASES; j++)
					rtype[pair[i][j]] = pair[j][i];
			}
		} else {
			for (i = 0; i <= MAXALPHA; i++) {
				for (j = 0; j <= MAXALPHA; j++)
					pair[i][j] = 0;
			}
			if (FoldVars.energy_set == 1) {
				for (i = 1; i < MAXALPHA;) {
					alias[i++] = 3; /* A <-> G */
					alias[i++] = 2; /* B <-> C */
				}
				for (i = 1; i < MAXALPHA; i++) {
					pair[i][i + 1] = 2; /* AB <-> GC */
					i++;
					pair[i][i - 1] = 1; /* BA <-> CG */
				}
			} else if (FoldVars.energy_set == 2) {
				for (i = 1; i < MAXALPHA;) {
					alias[i++] = 1; /* A <-> A */
					alias[i++] = 4; /* B <-> U */
				}
				for (i = 1; i < MAXALPHA; i++) {
					pair[i][i + 1] = 5; /* AB <-> AU */
					i++;
					pair[i][i - 1] = 6; /* BA <-> UA */
				}
			} else if (FoldVars.energy_set == 3) {
				for (i = 1; i < MAXALPHA - 2;) {
					alias[i++] = 3; /* A <-> G */
					alias[i++] = 2; /* B <-> C */
					alias[i++] = 1; /* C <-> A */
					alias[i++] = 4; /* D <-> U */
				}
				for (i = 1; i < MAXALPHA - 2; i++) {
					pair[i][i + 1] = 2; /* AB <-> GC */
					i++;
					pair[i][i - 1] = 1; /* BA <-> CG */
					i++;
					pair[i][i + 1] = 5; /* CD <-> AU */
					i++;
					pair[i][i - 1] = 6; /* DC <-> UA */
				}
			} else
				throw new InvalidConfigurationException("What energy_set are YOU using??");
			for (i = 0; i <= MAXALPHA; i++) {
				for (j = 0; j <= MAXALPHA; j++)
					rtype[pair[i][j]] = pair[j][i];
			}
		}
	}

}