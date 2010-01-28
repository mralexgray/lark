package com.stuffwithstuff.lark;

import java.util.*;

public class LarkParser extends Parser {
    
    //### bob: other stuff to support
    // keywords that follow first arg?
    //   a b: c
    // [] for s-expr-style lists
    // { a; b } that desugars to do (a, b)?
    // (+) for creating name from operator
    // (foo:) for creating name from keyword
    // strings
    // negative numbers and decimals
    
    public LarkParser(Lexer lexer) {
        super(lexer);
    }
    
    public Expr parse() {
        return list();
    }
    
    private Expr list() {
        List<Expr> exprs = new ArrayList<Expr>();
        
        do {
            exprs.add(keyword());
        } while (match(TokenType.COMMA));
        
        // only create a list if we actually had a ,
        if (exprs.size() == 1) return exprs.get(0);
        
        return new ListExpr(exprs);
    }
    
    private Expr keyword() {
        if (!isMatch(TokenType.KEYWORD)) return operator();

        List<String> keywords = new ArrayList<String>();
        List<Expr> args = new ArrayList<Expr>();

        while (match(TokenType.KEYWORD)) {
            keywords.add(getMatch()[0].getString());
            args.add(operator());
        }

        return new CallExpr(new NameExpr(join(keywords)), new ListExpr(args));
    }

    private Expr operator() {
        Expr expr = call();

        while (match(TokenType.OPERATOR)) {
            String op = getMatch()[0].getString();
            Expr right = call();

            List<Expr> args = new ArrayList<Expr>();
            args.add(expr);
            args.add(right);
            
            expr = new CallExpr(new NameExpr(op), new ListExpr(args));
        }

        return expr;        
    }
    
    private Expr call() {
        Stack<Expr> stack = new Stack<Expr>();
        
        // push as many calls as we can parse
        while (true) {
            Expr expr = dottedOrNull();
            if (expr == null) break;
            stack.push(expr);
        }
        
        //### bob: need error-handling
        if (stack.size() == 0) return new NameExpr("parse error, expected primary");
        
        // and then pop them back off to be right-associative
        Expr result = stack.pop();
        while (stack.size() > 0) {
            result = new CallExpr(stack.pop(), result);
        }
        
        return result;
    }

    private Expr dottedOrNull() {
        Expr expr = primaryOrNull();
        
        if (expr == null) return null;

        while (match(TokenType.DOT)) {
            Expr right = primaryOrNull();

            //### bob: need error-handling
            if (right == null) return new NameExpr("parse error, expected expression after '.'");
            
            // swap the function and argument
            // a.b -> b(a)
            expr = new CallExpr(right, expr);
        }

        return expr;        
    }
    
    private Expr primaryOrNull() {
        if (match(TokenType.NAME)) {
            String name = getMatch()[0].getString();
            
            // check for reserved names
            if (name.equals("true")) return new BoolExpr(true);
            if (name.equals("false")) return new BoolExpr(false);
            
            return new NameExpr(name);
            
        } else if (match(TokenType.NUMBER)) {
            return new IntExpr(getMatch()[0].getInt());
            
        } else if (match(TokenType.LEFT_PAREN)) {
            // () is unit
            if (match(TokenType.RIGHT_PAREN)) {
                return Expr.unit();
            }
            
            // handle (operator) and (keyword:) so that you can create
            // name exprs for operators and keywords without actually
            // having to parse them as used.
            if (match(TokenType.OPERATOR, TokenType.RIGHT_PAREN)) {
                return new NameExpr(getMatch()[0].getString());
            }
            if (match(TokenType.KEYWORD, TokenType.RIGHT_PAREN)) {
                return new NameExpr(getMatch()[0].getString());
            }
            
            Expr expr = list();
            
            if (!match(TokenType.RIGHT_PAREN)) {
                // no closing )
                //### bob: need error-handling!
                return new NameExpr("missing closing )!");
            }
            
            return expr;
        } else if (match(TokenType.LEFT_BRACE)) {
            
            Expr expr = doList();
            
            if (!match(TokenType.RIGHT_BRACE)) {
                // no closing )
                //### bob: need error-handling!
                return new NameExpr("missing closing )!");
            }
            
            return expr;
        }
        
        return null;
    }
    
    private Expr doList() {
        List<Expr> exprs = new ArrayList<Expr>();
        
        do {
            exprs.add(list());
        } while(match(TokenType.SEMICOLON));
        
        return new CallExpr(new NameExpr("do"), new ListExpr(exprs));        
    }
    
    private String join(Collection<?> s) {
        StringBuilder builder = new StringBuilder();
        Iterator<?> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
        }
        return builder.toString();
    }
}
