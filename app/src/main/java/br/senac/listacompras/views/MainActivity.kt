package br.senac.listacompras.views

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.senac.listacompras.databinding.ActivityMainBinding
import br.senac.listacompras.databinding.RowItemBinding
import br.senac.listacompras.model.Produto
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    var database: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Verifica se o Firebase está logado, caso não esteja, faz login
        //Se estiver, chama a função de configuração do Firebase
        tratarLogin()

        //Trata o clique no fab (botão de adição)
        binding.fab.setOnClickListener {
            //Chama a função para adição de um novo item
            novoItem()
        }
    }

    //Verifica se o usuário está logado. Se não estiver, faz login usando o Firebase UI
    //Se já estiver, configura a base do Firebase Realtime Database
    private fun tratarLogin() {
        //Pergunta a autenticação do Firebase se há usuário logado
        if (FirebaseAuth.getInstance().currentUser == null) {
            //Se não houver, monta uma lista de provedores de autenticação e chama o intent
            //SignInIntent para que o Firebase Auth UI faça a autenticação
            val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())
            startActivityForResult(
                AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build(),
                0
            )
        } else {
            //Se houver usuário logado, configura a base do realtime database
            configurarFirebase()
        }
    }

    fun configurarFirebase() {
        //Verifica se o usuário atual existe
        FirebaseAuth.getInstance().currentUser?.let {
            //Obtém a referência ao nó do usuário na base do realtime database
            database = FirebaseDatabase.getInstance().reference.child(it.uid)

            //Cria o listener de mudanças do nó no Firebase
            val itemListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    //Função executada quando algo no nó mudar
                    //Trata os dados do produto no nó
                    tratarDadosProdutos(dataSnapshot)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    //Função chamada quando houver erro de conexão
                    Log.w("MainActivity", "onCancelled", databaseError.toException())

                    Toast.makeText(this@MainActivity, "Erro ao acessar o servidor",
                        Toast.LENGTH_LONG);
                }
            }

            //Configura o listener no nó de produtos
            database?.child("produtos")?.addValueEventListener(itemListener)
        }
    }

    //Função chamada quando a atividade devolver uma resposta
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            //Se a autenticação der certo, configura a base para o usuário
            Toast.makeText(this, "Autenticado", Toast.LENGTH_LONG).show()
            configurarFirebase()
        } else {
            //Caso a autenticação falhe, fecha o aplicativo
            finishAffinity()
        }
    }

    //Abre o diálogo para criação de um novo item
    fun novoItem() {
        //Configura um campo de texto para exibir no diálogo
        val editText = EditText(this)
        editText.hint = "Nome do item"

        //Cria e exiba um novo alerta com campo de texto
        AlertDialog.Builder(this)
            .setTitle(("Adicionar Item"))
            .setView(editText)
            .setPositiveButton("Adicionar") { dialog, button ->
                //Cria um novo produto e salva na base
                val prod = Produto(nome = editText.text.toString(), comprado = false)
                val noNovoProduto = database?.child("produtos")?.push()
                prod.id = noNovoProduto?.key
                noNovoProduto?.setValue(prod)
            }
            .show()
    }

    //Tratar os dados da "foto" do Firebase, transformando-os em
    //uma lista de produtos e chamadno a função "atualizarTela"
    //para exibí-los
    private fun tratarDadosProdutos(dataSnapshot: DataSnapshot) {
        val itemList = arrayListOf<Produto>()

        //Percorre os nós filhos da foto para transformá-los
        //em uma lista de produtos
        dataSnapshot.children.forEach {
            val prod = it.getValue(Produto::class.java)
            prod?.let {
                itemList.add(it);
            }
        }

        //Chamar o método que lerá array itemList e atualizará a tela
        atualizarTela(itemList)
    }

    //Função que atualiza a tela com a lista de produtos
    fun atualizarTela(list: List<Produto>) {
        //Limpa a tela
        binding.container.removeAllViews()

        //Percorre os itens da lista
        list.forEach {
            //Instancia um novo item de cartão
            val rowItem = RowItemBinding.inflate(layoutInflater)

            //Configura os itens do layout de acordo com o produto
            rowItem.nome.text = it.nome
            rowItem.comprado.isChecked = it.comprado

            //Armazena o id para uso nas ações de marcação e exclusão
            val id = it.id as String;

            //Trata o clique no checkbox "comprado"
            rowItem.comprado.setOnCheckedChangeListener { buttonView, isChecked ->
                //Obtem o item da base de dados usando o id
                val itemReference = database?.child("produtos")?.child(id)
                //Solicita a mudança da propriedade comprado de acordo com o valor do checkbox
                itemReference?.child("comprado")?.setValue(isChecked);
            }

            //Trata o clique no botão "excluir"
            rowItem.excluir.setOnClickListener {
                //Obtem o item da base de dados usando o id
                val itemReference = database?.child("produtos")?.child(id)
                //Remove o item
                itemReference?.removeValue()
            }

            //Adiciona o item dentro do linear layout da scrollview, para que apareça na tela
            binding.container.addView(rowItem.root)
        }
    }

}