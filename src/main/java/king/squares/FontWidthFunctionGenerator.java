package king.squares;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import it.unimi.dsi.fastutil.ints.Int2BooleanArrayMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import static com.github.javaparser.ast.NodeList.nodeList;

public class FontWidthFunctionGenerator {

  private static final int CHECKPOINT_THRESHOLD = Integer.MAX_VALUE - 1; //Not used rn

  private final NameExpr widthVariable = new NameExpr("width");
  private final NameExpr codepointParameter = new NameExpr("codepoint");
  private final CompilationUnit unit;
  private final ClassOrInterfaceDeclaration clazz;
  private final Map<Float, List<@NotNull Integer>> sizes;
  private final Int2BooleanArrayMap boldOffsets;
  private final int totalCodepointAmount;

  public FontWidthFunctionGenerator(final Map<Float, List<@NotNull Integer>> sizes, final Int2BooleanArrayMap boldOffsets, final boolean java14) {
    this.unit = new CompilationUnit();
    final NameExpr className = new NameExpr("FontWidthFunction");
    this.clazz = this.unit.addClass(className.getNameAsString(), Modifier.Keyword.PUBLIC);
    this.sizes = sizes;
    this.boldOffsets = boldOffsets;
    this.totalCodepointAmount = sizes.values().stream().mapToInt(List::size).sum();

    this.unit
        .addImport(IOException.class)
        .addImport(Style.class)
        .addImport(TextDecoration.class)
        .addImport(NotNull.class)
        .setPackageDeclaration("king.squares");

    final FieldDeclaration instanceField = this.clazz.addField(className.getNameAsString(), "INSTANCE", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);
    instanceField.getVariable(0).setInitializer(new ObjectCreationExpr().setType(StaticJavaParser.parseClassOrInterfaceType(className.getNameAsString())));

    if (this.totalCodepointAmount > CHECKPOINT_THRESHOLD) this.createCheckpointSystem();

    this.createMainMethod(this.clazz.addMethod("widthOf", Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL), java14);

  }

  public String makeClass() {
    return this.unit.toString();
  }

  private void createMainMethod(final MethodDeclaration method, final boolean java14) {
    final ClassOrInterfaceType IOExceptionType = StaticJavaParser.parseClassOrInterfaceType("IOException");
    method.setType(float.class);
    method.setThrownExceptions(NodeList.nodeList(IOExceptionType));
    method.addAndGetParameter(int.class, this.codepointParameter.getNameAsString()).setFinal(true);
    method.addAndGetParameter(Style.class, "style").setFinal(true).addAnnotation(NotNull.class);

    final BlockStmt body = new BlockStmt();

    // int i;
    final VariableDeclarator variableDeclarator = new VariableDeclarator();
    variableDeclarator
        .setName(this.widthVariable.getName())
        .setType(float.class)
        .setInitializer("-1");

    final SwitchStmt switchStmt = new SwitchStmt(this.codepointParameter, this.createSizeSwitchStatements(java14));

    final Statement calculateBoldStatement = this.createBoldCondition();

    Style.empty().hasDecoration(TextDecoration.BOLD);

    final IfStmt checkIInitialized =
        new IfStmt()
            .setCondition(new NameExpr("width == -1"))
            .setThenStmt(
                new ThrowStmt(
                    new ObjectCreationExpr(null, IOExceptionType, NodeList.nodeList(new StringLiteralExpr("Could not calculate the width of\" + " + this.codepointParameter.getNameAsString() + " + \"")))));

    final ReturnStmt returnStmt = new ReturnStmt(this.widthVariable);

    body.addStatement(new VariableDeclarationExpr(variableDeclarator));
    body.addStatement(switchStmt);
    body.addStatement(calculateBoldStatement);
    body.addStatement(checkIInitialized);
    body.addStatement(returnStmt);

    method.setBody(body);
  }

  private void createCheckpointSystem() {
    final FieldDeclaration checkpointArray = this.clazz.addField(int[].class, "checkpoints", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
    checkpointArray.getVariable(0).setInitializer(this.createCheckpoints());
    this.createCheckpointCalculatorMethod(this.clazz.addMethod("findCheckpoint", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL));
  }

  private void createCheckpointCalculatorMethod(final MethodDeclaration method) {
    method.setType(int.class);
    method.addParameter(new Parameter(StaticJavaParser.parseType("int"), "c").addModifier(Modifier.Keyword.FINAL));

    final BlockStmt body = new BlockStmt();

    final VariableDeclarator passedBoolean = new VariableDeclarator();
    passedBoolean.setInitializer(new BooleanLiteralExpr());
    passedBoolean.setType(boolean.class);
    passedBoolean.setName("passed");

    final VariableDeclarationExpr passedBooleanExpr = new VariableDeclarationExpr(passedBoolean);
    body.addStatement(passedBooleanExpr);
    body.addStatement(this.createCheckpointForLoop(passedBooleanExpr));

    method.setBody(body);
  }

  private ForStmt createCheckpointForLoop(final VariableDeclarationExpr passedBoolean) {
    final ForStmt stmt = new ForStmt();

    stmt.setInitialization(NodeList.nodeList(new VariableDeclarationExpr(new VariableDeclarator(StaticJavaParser.parseType("int"), "i", new NameExpr("0")))));
    stmt.setCompare(new NameExpr("i < this.checkpoints.length"));
    stmt.setUpdate(NodeList.nodeList(new NameExpr("i++")));

    final BlockStmt body = new BlockStmt();
    body.addStatement(this.createCheckpointHitCondition(passedBoolean));
    body.addStatement(new ReturnStmt(new NameExpr("-1")));
    stmt.setBody(body);

    return stmt;
  }

  private IfStmt createCheckpointHitCondition(final VariableDeclarationExpr passedBoolean) {
    final IfStmt stmt = new IfStmt();
    stmt.setCondition(new NameExpr("this.checkpoints[i]"));
    final BlockStmt thenStmt = new BlockStmt();
    thenStmt.addStatement(new AssignExpr(passedBoolean, new BooleanLiteralExpr(true), AssignExpr.Operator.ASSIGN));
    thenStmt.addStatement(new ReturnStmt(new NameExpr("this.checkpoints[i]")));
    stmt.setThenStmt(thenStmt);

    return stmt;
  }

  //True if able to use enchanced switch labes (multiple numbers on one case)
  private NodeList<SwitchEntry> createSizeSwitchStatements(boolean java14) {
    final NodeList<SwitchEntry> switchEntries = nodeList();

    for (final Map.Entry<Float, List<Integer>> entry : this.sizes.entrySet()) {
      final List<Integer> glyphs = entry.getValue();
      final NodeList<Expression> labels = nodeList();

      final ExpressionStmt assignValue =
          new ExpressionStmt(
              new AssignExpr()
                  .setTarget(this.widthVariable)
                  .setValue(new DoubleLiteralExpr(entry.getKey() + "F")));

      if (java14) {
        for (final Integer glyph : glyphs) {
          labels.add(new IntegerLiteralExpr(String.valueOf(glyph))); //TODO:stream map
        }

        if (!labels.isEmpty())
          switchEntries.add(
              new SwitchEntry()
                  .setLabels(labels)
                  .setStatements(nodeList(assignValue, new BreakStmt().removeLabel())));
      } else {
        for (final Integer glyph : glyphs) {
          switchEntries.add(new SwitchEntry().setLabels(nodeList(new IntegerLiteralExpr(String.valueOf(glyph)))));
        }
        switchEntries.getLast().orElse(new SwitchEntry()).setStatements(nodeList(assignValue, new BreakStmt().removeLabel()));
      }
    }

    return switchEntries;
  }

  private Statement createBoldCondition() {
    //fast track if only bitmap/ttf glyphs are used
    if (!this.boldOffsets.values().contains(false)) {
      return new IfStmt()
          .setCondition(new NameExpr("width != -1 && style.hasDecoration(TextDecoration.BOLD)"))
          .setThenStmt(new ExpressionStmt(new NameExpr("width++")));
    }

    final SwitchEntry switchEntry1 = new SwitchEntry();
    switchEntry1.setStatements(nodeList(new ExpressionStmt(new AssignExpr(this.widthVariable, new DoubleLiteralExpr(1 + "F"), AssignExpr.Operator.PLUS)), new BreakStmt().removeLabel()));
    final NodeList<Expression> labels1 = nodeList();
    final SwitchEntry switchEntry0_5 = new SwitchEntry();
    switchEntry0_5.setStatements(nodeList(new ExpressionStmt(new AssignExpr(this.widthVariable, new DoubleLiteralExpr(0.5 + "F"), AssignExpr.Operator.PLUS)), new BreakStmt().removeLabel()));
    final NodeList<Expression> labels0_5 = nodeList();

    for (final Int2BooleanMap.Entry entry : this.boldOffsets.int2BooleanEntrySet()) {
      if (entry.getBooleanValue()) labels1.add(new IntegerLiteralExpr("" + entry.getIntKey()));
      else labels0_5.add(new IntegerLiteralExpr("" + entry.getIntKey()));
    }

    switchEntry1.setLabels(labels1);
    switchEntry0_5.setLabels(labels0_5);

    return new SwitchStmt(this.codepointParameter, nodeList(switchEntry1, switchEntry0_5));
  }

  private ArrayCreationExpr createCheckpoints() {
    final ArrayCreationExpr expr = new ArrayCreationExpr();
    expr.setElementType(int.class);
    final ArrayInitializerExpr arrayInitExpr = new ArrayInitializerExpr();
    final List<Integer> checkpoints = new ArrayList<>(this.totalCodepointAmount);

    for (final Map.Entry<Float, List<Integer>> entry : this.sizes.entrySet()) {
      int currentCheckpoint = -2;
      for (final int i : entry.getValue()) {
        if (i - currentCheckpoint != 1) {
          //Not number neighbours
          if (currentCheckpoint != -2) checkpoints.add(currentCheckpoint);
          currentCheckpoint = i;
        } else currentCheckpoint++;
      }
    }
    checkpoints.sort(Comparator.comparingInt(Integer::intValue));
    final NodeList<Expression> arrayValues = checkpoints.stream().map(i -> new IntegerLiteralExpr("" + i)).collect(NodeList.toNodeList());

    arrayInitExpr.setValues(arrayValues);
    expr.setInitializer(arrayInitExpr);

    return expr;
  }
}
