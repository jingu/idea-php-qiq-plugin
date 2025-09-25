/* src/main/kotlin/io/github/jingu/idea_qiq_plugin/lexer/Qiq.flex */
/* JFlex lexer for Qiq */
package io.github.jingu.idea_qiq_plugin.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes;

%%

%class _QiqLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%state QIQ, QIQ_RAW, QIQ_HTML, QIQ_JSON, QIQ_URL, QIQ_ESC, PHP

/* --------- Macros --------- */
WHITE_SPACE = [ \t\f\r\n]+
IDENT       = [a-zA-Z_][a-zA-Z0-9_]*
NUMBER      = [0-9]+
STRING      = (\"([^\"\\]|\\.)*\")|('(\\.|[^'\\])*')
HTML_TAG    = "<"[^?{][^>]*">"?
PHP_CHUNK   = ([^?]|\?[^>])+

%%

/* --------- Initial (outer template data) --------- */
<YYINITIAL> {
  "{{="       { yybegin(QIQ_RAW); return QiqTokenTypes.RAW_OPEN; }
  "{{h"       { yybegin(QIQ_HTML); return QiqTokenTypes.ESCAPE_H_OPEN; }
  "{{a"       { yybegin(QIQ_ESC);  return QiqTokenTypes.ESCAPE_A_OPEN; }
  "{{j"       { yybegin(QIQ_JSON); return QiqTokenTypes.ESCAPE_J_OPEN; }
  "{{u"       { yybegin(QIQ_URL);  return QiqTokenTypes.ESCAPE_U_OPEN; }
  "{{c"       { yybegin(QIQ_ESC);  return QiqTokenTypes.ESCAPE_C_OPEN; }
  "{{"        { yybegin(QIQ);      return QiqTokenTypes.CODE_OPEN; }
  "<\\?php"       { yybegin(PHP);     return QiqTokenTypes.PHP_OPEN; }
  "<\?php"        { yybegin(PHP);     return QiqTokenTypes.PHP_OPEN; }
  {WHITE_SPACE}+   { return QiqTokenTypes.WHITE_SPACE; }
  {HTML_TAG}       {
    String text = yytext().toString();
    int braceIndex = text.indexOf("{{");
    if (braceIndex >= 0) {
      int pushback = text.length() - braceIndex;
      yypushback(pushback);
      if (braceIndex == 0) {
        continue;
      }
      return QiqTokenTypes.TEMPLATE_DATA;
    }
    return QiqTokenTypes.TEMPLATE_DATA;
  }
  [^\{\}<]+       {
    String text = yytext().toString();
    int braceIndex = text.indexOf("{{");
    if (braceIndex >= 0) {
      int pushback = text.length() - braceIndex;
      yypushback(pushback);
      if (braceIndex == 0) {
        continue;
      }
      return QiqTokenTypes.TEMPLATE_DATA;
    }
    return QiqTokenTypes.TEMPLATE_DATA;
  }
  "<"               { return QiqTokenTypes.TEMPLATE_DATA; }
  [\{\}]          { return QiqTokenTypes.TEMPLATE_DATA; }
}

/* --------- Qiq block --------- */
<QIQ> {
  "}}"                              { yybegin(YYINITIAL); return QiqTokenTypes.RBRACE2; }
  \b(endif|endfor|endforeach)\b     { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b         { return QiqTokenTypes.DIRECTIVE_OPEN; } // 実運用では細分化
  {IDENT}                           { return QiqTokenTypes.IDENT; }
  {NUMBER}                          { return QiqTokenTypes.NUMBER; }
  {STRING}                          { return QiqTokenTypes.STRING; }
  "("                               { return QiqTokenTypes.PAREN_L; }
  ")"                               { return QiqTokenTypes.PAREN_R; }
  ","                               { return QiqTokenTypes.COMMA; }
  ":"                               { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+                    { return QiqTokenTypes.WHITE_SPACE; }
  [^]                               { return QiqTokenTypes.BAD_CHAR; }
  [^}]+                             { return QiqTokenTypes.CODE_BODY; }
  "}"                               { return QiqTokenTypes.CODE_BODY; }
}

/* --------- Qiq RAW block ({{= ... }}) --------- */
<QIQ_RAW> {
  "}}"            { yybegin(YYINITIAL); return QiqTokenTypes.RBRACE_EQ; }
  \b(endif|endfor|endforeach)\b   { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b       { return QiqTokenTypes.DIRECTIVE_OPEN; }
  {IDENT}         { return QiqTokenTypes.IDENT; }
  {NUMBER}        { return QiqTokenTypes.NUMBER; }
  {STRING}        { return QiqTokenTypes.STRING; }
  "("             { return QiqTokenTypes.PAREN_L; }
  ")"             { return QiqTokenTypes.PAREN_R; }
  ","             { return QiqTokenTypes.COMMA; }
  ":"             { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+  { return QiqTokenTypes.WHITE_SPACE; }
  [^]             { return QiqTokenTypes.BAD_CHAR; }
  [^}]+            { return QiqTokenTypes.CODE_BODY; }
  "}"             { return QiqTokenTypes.CODE_BODY; }
}

/* --------- Qiq HTML-escaped block ({{h ... }}) --------- */
<QIQ_HTML> {
  "}}"            { yybegin(YYINITIAL); return QiqTokenTypes.RBRACEH; }
  \b(endif|endfor|endforeach)\b   { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b       { return QiqTokenTypes.DIRECTIVE_OPEN; }
  {IDENT}         { return QiqTokenTypes.IDENT; }
  {NUMBER}        { return QiqTokenTypes.NUMBER; }
  {STRING}        { return QiqTokenTypes.STRING; }
  "("             { return QiqTokenTypes.PAREN_L; }
  ")"             { return QiqTokenTypes.PAREN_R; }
  ","             { return QiqTokenTypes.COMMA; }
  ":"             { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+  { return QiqTokenTypes.WHITE_SPACE; }
  [^]             { return QiqTokenTypes.BAD_CHAR; }
  [^}]+            { return QiqTokenTypes.CODE_BODY; }
  "}"             { return QiqTokenTypes.CODE_BODY; }
}

/* --------- Qiq JSON-escaped block ({{j ... }}) --------- */
<QIQ_JSON> {
  "}}"            { yybegin(YYINITIAL); return QiqTokenTypes.RBRACEJ; }
  \b(endif|endfor|endforeach)\b   { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b       { return QiqTokenTypes.DIRECTIVE_OPEN; }
  {IDENT}         { return QiqTokenTypes.IDENT; }
  {NUMBER}        { return QiqTokenTypes.NUMBER; }
  {STRING}        { return QiqTokenTypes.STRING; }
  "("             { return QiqTokenTypes.PAREN_L; }
  ")"             { return QiqTokenTypes.PAREN_R; }
  ","             { return QiqTokenTypes.COMMA; }
  ":"             { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+  { return QiqTokenTypes.WHITE_SPACE; }
  [^]             { return QiqTokenTypes.BAD_CHAR; }
  [^}]+            { return QiqTokenTypes.CODE_BODY; }
  "}"             { return QiqTokenTypes.CODE_BODY; }
}

/* --------- Qiq URL-escaped block ({{u ... }}) --------- */
<QIQ_URL> {
  "}}"            { yybegin(YYINITIAL); return QiqTokenTypes.RBRACEU; }
  \b(endif|endfor|endforeach)\b   { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b       { return QiqTokenTypes.DIRECTIVE_OPEN; }
  {IDENT}         { return QiqTokenTypes.IDENT; }
  {NUMBER}        { return QiqTokenTypes.NUMBER; }
  {STRING}        { return QiqTokenTypes.STRING; }
  "("             { return QiqTokenTypes.PAREN_L; }
  ")"             { return QiqTokenTypes.PAREN_R; }
  ","             { return QiqTokenTypes.COMMA; }
  ":"             { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+  { return QiqTokenTypes.WHITE_SPACE; }
  [^]             { return QiqTokenTypes.BAD_CHAR; }
  [^}]+            { return QiqTokenTypes.CODE_BODY; }
  "}"             { return QiqTokenTypes.CODE_BODY; }
}

/* --------- Qiq generic escaped block ({{a ... }}, {{c ... }}) --------- */
<QIQ_ESC> {
  "}}"            { yybegin(YYINITIAL); return QiqTokenTypes.RBRACEC; }
  \b(endif|endfor|endforeach)\b   { return QiqTokenTypes.DIRECTIVE_CLOSE; }
  \b(if|for|foreach|else)\b       { return QiqTokenTypes.DIRECTIVE_OPEN; }
  {IDENT}         { return QiqTokenTypes.IDENT; }
  {NUMBER}        { return QiqTokenTypes.NUMBER; }
  {STRING}        { return QiqTokenTypes.STRING; }
  "("             { return QiqTokenTypes.PAREN_L; }
  ")"             { return QiqTokenTypes.PAREN_R; }
  ","             { return QiqTokenTypes.COMMA; }
  ":"             { return QiqTokenTypes.COLON; }
  "=="|"==="|"!="|">="|"<="|"&&"|"||"|[+\-*/<>.=] { return QiqTokenTypes.OP; }
  {WHITE_SPACE}+  { return QiqTokenTypes.WHITE_SPACE; }
  [^]             { return QiqTokenTypes.BAD_CHAR; }
  [^}]+            { return QiqTokenTypes.CODE_BODY; }
  "}"             { return QiqTokenTypes.CODE_BODY; }
}

/* --------- PHP island (we don't lex PHP itself here) --------- */
<PHP> {
  "\?>"           { yybegin(YYINITIAL); return QiqTokenTypes.PHP_CLOSE; }
  {PHP_CHUNK}      { return QiqTokenTypes.PHP_CONTENT; }
}


<<EOF>> { return null; }
