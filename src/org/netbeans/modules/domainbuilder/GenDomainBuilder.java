/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.domainbuilder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.openide.util.Lookup;
import sun.tools.tree.ThisExpression;

public class GenDomainBuilder implements CodeGenerator {

    JTextComponent textComp;

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private GenDomainBuilder( Lookup context ) { // Good practice is not to save Lookup outside ctor
        textComp = context.lookup( JTextComponent.class );
    }

    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        public List<? extends CodeGenerator> create( Lookup context ) {
            return Collections.singletonList( new GenDomainBuilder( context ) );
        }
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    public String getDisplayName() {
        return "Domain From and To";
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    public void invoke() {
        try
        {

            Document doc = textComp.getDocument();
            JavaSource javaSource = JavaSource.forDocument( doc );
            CancellableTask task = new CancellableTask<WorkingCopy>() {
                public void run( WorkingCopy workingCopy ) throws IOException {
                    workingCopy.toPhase( Phase.RESOLVED );
                    CompilationUnitTree cut = workingCopy.getCompilationUnit();
                    TreeMaker make = workingCopy.getTreeMaker();

                    cut.getTypeDecls()
                            .stream()
                            .filter( typeDecl -> Tree.Kind.CLASS == typeDecl.getKind() )
                            .map( typeDecl -> (ClassTree) typeDecl ).forEach( new Consumer<ClassTree>() {
                        @Override
                        public void accept( ClassTree clazz ) {
                            StringBuilder builder = new StringBuilder();
                            StringBuilder builder2 = new StringBuilder();
                            List<VariableTree> variableTrees = clazz.getMembers().stream().filter( t -> Tree.Kind.VARIABLE == t.getKind() ).map( t -> (VariableTree) t ).collect( Collectors.toList() );
                            List<VariableTree> vTree = new ArrayList<>();
                            List<ExpressionStatementTree> expressions = variableTrees.stream().map( classVariable ->
                            {
                                String variableName = classVariable.getName().toString();
                                VariableTree parameter
                                             = make.Variable( make.Modifiers( Set.of(),
                                                                              classVariable.getModifiers().getAnnotations() ),
                                                              variableName,
                                                              classVariable.getType(),
                                                              null );
                                vTree.add( parameter );
                                builder.append( String.format( "%s,", variableName  ));
                                if(variableName!=null){
                                     builder2.append( String.format( "%s.%s(),", clazz.getSimpleName().toString().toLowerCase().substring( 0, clazz.getSimpleName().toString().length() - 3), variableName ) );
                                }
                                return make.ExpressionStatement(
                                        make.Assignment(
                                                make.MemberSelect( make.QualIdent( "this" ), variableName ),
                                                make.QualIdent( variableName )
                                        )
                                );

                            }
                            ).collect( Collectors.toList() );

                            //Domian Construtors
                            MethodTree construtors = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC ),
                                            List.of() ),
                                    clazz.getSimpleName().toString(),
                                    (Tree) null,
                                    Collections.<TypeParameterTree>emptyList(),
                                    vTree,
                                    Collections.<ExpressionTree>emptyList(),
                                    make.Block(
                                            expressions,
                                            false ),
                                    null );

                            // Domain From
                            MethodTree fromDomain = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC, Modifier.STATIC ),
                                            List.of() ),
                                    "fromDomain",
                                    make.Type( clazz.getSimpleName().toString() ),
                                    Collections.<TypeParameterTree>emptyList(),
                                    List.of(
                                            make.Variable( make.Modifiers( Set.of(),
                                                                           List.of()),
                                                           clazz.getSimpleName().toString().toLowerCase().substring( 0, clazz.getSimpleName().toString().length() - 3),
                                                           make.Type( clazz.getSimpleName().toString().substring( 0, clazz.getSimpleName().toString().length() - 3)),
                                                           null )
                                    ),
                                    Collections.<ExpressionTree>emptyList(),String.format( "{ return new %s(%s) ;}", clazz.getSimpleName().toString() ,builder2.toString()),
                                    null );
                            
                             // Domain To
                            MethodTree toDomain = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC ),
                                            List.of() ),
                                    "toDomain",
                                    make.Type( clazz.getSimpleName().toString().substring( 0, clazz.getSimpleName().toString().length() - 3) ),
                                    Collections.<TypeParameterTree>emptyList(),
                                    Collections.<VariableTree>emptyList(),
                                    Collections.<ExpressionTree>emptyList(),
                                    String.format(
                                            "{return %s.create(%s);}", 
                                            clazz.getSimpleName().toString().substring( 0, clazz.getSimpleName().toString().length() - 3), 
                                            builder.toString().substring( 0, builder.toString().length() -1)
                                    ),
                                    null );

                            ClassTree updateWithConstrustor = make.addClassMember( clazz, construtors );
                            ClassTree updateWithFromDomain = make.addClassMember( updateWithConstrustor, fromDomain );
                            ClassTree updateWithToDomain = make.addClassMember( updateWithFromDomain, toDomain );
                            workingCopy.rewrite( clazz, updateWithToDomain );
                        }
                    } );
                }

                public void cancel() {
                }
            };
            ModificationResult result = javaSource.runModificationTask( task );
            result.commit();

        } catch (IOException | IllegalArgumentException ex)
        {
            ex.printStackTrace();
        }
    }

}
