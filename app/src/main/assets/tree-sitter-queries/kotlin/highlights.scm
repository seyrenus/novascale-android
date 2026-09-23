(simple_identifier) @variable

(this_expression) @variable.builtin
(super_expression) @variable.builtin

(type_identifier) @type
(nullable_type) @punctuation.special

(function_declaration
  (simple_identifier) @function.declaration)

(getter
  "get" @function.builtin)
(setter
  "set" @function.builtin)

(primary_constructor
  "constructor" @keyword)
(secondary_constructor
  "constructor" @keyword)

(parameter
  (simple_identifier) @parameter)
(parameter_with_optional_type
  (simple_identifier) @parameter)

(call_expression
  . (simple_identifier) @function.invocation)

(callable_reference
  . (simple_identifier) @function.invocation)

(call_expression
  (navigation_expression
    (navigation_suffix
      (simple_identifier) @function.invocation) .))

[(line_comment) (multiline_comment)] @comment

(shebang_line) @preproc

(real_literal) @number
[
  (integer_literal)
  (long_literal)
  (hex_literal)
  (bin_literal)
  (unsigned_literal)
] @number

[
  "null"
  (boolean_literal)
] @constant.builtin

(character_literal) @string
(string_literal) @string

(type_alias "typealias" @keyword)
(companion_object "companion" @keyword)

[
  (class_modifier)
  (member_modifier)
  (function_modifier)
  (property_modifier)
  (platform_modifier)
  (variance_modifier)
  (parameter_modifier)
  (visibility_modifier)
  (reification_modifier)
  (inheritance_modifier)
] @type.qualifier

[
  "package"
  "import"
  "val"
  "var"
  "enum"
  "class"
  "object"
  "interface"
  "fun"
  "for"
  "do"
  "while"
  "try"
  "catch"
  "throw"
  "finally"
  "if"
  "else"
  "when"
  "return"
  "continue"
  "break"
] @keyword

(label) @label

(annotation
  "@" @attribute)
(file_annotation
  "@" @attribute)

[
  "!"
  "!="
  "!=="
  "="
  "=="
  "==="
  ">"
  ">="
  "<"
  "<="
  "||"
  "&&"
  "+"
  "++"
  "+="
  "-"
  "--"
  "-="
  "*"
  "*="
  "/"
  "/="
  "%"
  "%="
  "?."
  "?:"
  "!!"
  "is"
  "!is"
  "in"
  "!in"
  "as"
  "as?"
  ".."
  "->"
  "."
  ","
  ";"
  ":"
  "::"
] @operator
