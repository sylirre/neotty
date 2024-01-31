#include <wctype.h>

static int custom_iswprint(wint_t wc)
{
    if (wc < 0xffU)
        return (wc+1 & 0x7f) >= 0x21;
    if (wc < 0x2028U || wc-0x202aU < 0xd800-0x202a || wc-0xe000U < 0xfff9-0xe000)
        return 1;
    if (wc-0xfffcU > 0x10ffff-0xfffc || (wc&0xfffe)==0xfffe)
        return 0;
    return 1;
}

int iswgraph(wint_t wc)
{
	/* ISO C defines this function as: */
	return !iswspace(wc) && custom_iswprint(wc);
}
