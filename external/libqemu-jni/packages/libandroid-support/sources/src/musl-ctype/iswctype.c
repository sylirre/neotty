#include <wchar.h>
#include <wctype.h>
#include <string.h>

#define WCTYPE_ALNUM  1
#define WCTYPE_ALPHA  2
#define WCTYPE_BLANK  3
#define WCTYPE_CNTRL  4
#define WCTYPE_DIGIT  5
#define WCTYPE_GRAPH  6
#define WCTYPE_LOWER  7
#define WCTYPE_PRINT  8
#define WCTYPE_PUNCT  9
#define WCTYPE_SPACE  10
#define WCTYPE_UPPER  11
#define WCTYPE_XDIGIT 12

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

int iswctype(wint_t wc, wctype_t type)
{
	switch (type) {
	case WCTYPE_ALNUM:
		return iswalnum(wc);
	case WCTYPE_ALPHA:
		return iswalpha(wc);
	case WCTYPE_BLANK:
		return iswblank(wc);
	case WCTYPE_CNTRL:
		return iswcntrl(wc);
	case WCTYPE_DIGIT:
		return iswdigit(wc);
	case WCTYPE_GRAPH:
		return iswgraph(wc);
	case WCTYPE_LOWER:
		return iswlower(wc);
	case WCTYPE_PRINT:
		return custom_iswprint(wc);
	case WCTYPE_PUNCT:
		return iswpunct(wc);
	case WCTYPE_SPACE:
		return iswspace(wc);
	case WCTYPE_UPPER:
		return iswupper(wc);
	case WCTYPE_XDIGIT:
		return iswxdigit(wc);
	}
	return 0;
}

wctype_t wctype(const char *s)
{
	int i;
	const char *p;
	/* order must match! */
	static const char names[] =
		"alnum\0" "alpha\0" "blank\0"
		"cntrl\0" "digit\0" "graph\0"
		"lower\0" "print\0" "punct\0"
		"space\0" "upper\0" "xdigit";
	for (i=1, p=names; *p; i++, p+=6)
		if (*s == *p && !strcmp(s, p))
			return i;
	return 0;
}
